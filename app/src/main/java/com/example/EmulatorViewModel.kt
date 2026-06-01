package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class EmulatorStatus {
    IDLE,
    CREATING,
    PAUSED,
    ERROR,
    COMPLETED
}

enum class LogType {
    INFO,
    DEBUG,
    WARNING,
    ERROR,
    SUCCESS
}

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String,
    val tag: String,
    val message: String,
    val type: LogType
)

class EmulatorViewModel : ViewModel() {

    // Configuration Settings
    private val _ramSizeGb = MutableStateFlow(8)
    val ramSizeGb: StateFlow<Int> = _ramSizeGb.asStateFlow()

    private val _cpuCores = MutableStateFlow(4)
    val cpuCores: StateFlow<Int> = _cpuCores.asStateFlow()

    private val _sdCardSizeGb = MutableStateFlow(16)
    val sdCardSizeGb: StateFlow<Int> = _sdCardSizeGb.asStateFlow()

    private val _gpuRendering = MutableStateFlow("Vulkan 1.3 - Hardware")
    val gpuRendering: StateFlow<String> = _gpuRendering.asStateFlow()

    private val _deviceName = MutableStateFlow("Google Pixel 10 (Beta API 36)")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    // Working & Progress State
    private val _status = MutableStateFlow(EmulatorStatus.IDLE)
    val status: StateFlow<EmulatorStatus> = _status.asStateFlow()

    private val _currentFileIndex = MutableStateFlow(0)
    val currentFileIndex: StateFlow<Int> = _currentFileIndex.asStateFlow()

    private val _currentFileProgress = MutableStateFlow(0f)
    val currentFileProgress: StateFlow<Float> = _currentFileProgress.asStateFlow()

    private val _totalBytesWritten = MutableStateFlow(0L)
    val totalBytesWritten: StateFlow<Long> = _totalBytesWritten.asStateFlow()

    private val _writeSpeedMbs = MutableStateFlow(0.0)
    val writeSpeedMbs: StateFlow<Double> = _writeSpeedMbs.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Device Storage Info
    private val _freeStorageBytes = MutableStateFlow(0L)
    val freeStorageBytes: StateFlow<Long> = _freeStorageBytes.asStateFlow()

    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    private var workerJob: Job? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val techLogPool = listOf(
        "Инициализация гипервизора VMX... Найдено аппаратное ускорение KVM.",
        "Определение маппинга адресов физической памяти виртуальной машины.",
        "Настройка виртуальной сетевой карты G-Bridge (интерфейс virbr0).",
        "Компиляция набора инструкций ELF x86_64 в транслятор ARM64.",
        "Подготовка разметки GPT виртуального накопителя system.img...",
        "Монтирование логического раздела ядра /vendor в режиме READ-ONLY.",
        "Загрузка бинарных библиотек рендеринга API Vulkan 1.3 кэша шейдеров.",
        "Применение политики безопасности SELinux Context (Enforcing mode).",
        "Форматирование раздела userdata.img в файловую систему f2fs...",
        "Создание дерева объектов виртуального устройства 'Pixel 10'...",
        "Загрузка системного пакета HAL модулей для камеры (API level 36).",
        "Трансляция байт-кода dalvik.system.DexClassLoader для приложений ядра.",
        "Оптимизация фреймворка: компиляция boot.art с использованием профиля JIT.",
        "Выделение непрерывного кэш-буфера под виртуальный графический чипсет (GPU AMD Radeon Pro).",
        "Инициализация системного аудио-сервера AudioFlinger...",
        "Сборка пакета метаданных образов раздела initrd...",
        "Сжатие заголовков системных разделов методом lz4-high...",
        "Проверка целостности зашифрованных ключей подписи виртуальной песочницы.",
        "Загрузка структуры ядра Android Linux Kernel v6.12-rc5...",
        "Конфигурирование межпроцессного взаимодействия через IPC-драйвер Binder.",
        "Настройка контроллера памяти c_groups для изоляции контейнера эмулятора.",
        "Обновление заголовков цепочки разметки дисковой подсистемы (block_dev: 0x2A)."
    )

    init {
        // Initial log entries
        addLog("SYSTEM", "Менеджер конфигураций Emulator GUI v17.0.2 готов к работе.", LogType.INFO)
        addLog("HOST", "Загрузка системного профиля хост-устройства...", LogType.DEBUG)
    }

    fun setRamSize(gb: Int) {
        _ramSizeGb.value = gb
    }

    fun setCpuCores(cores: Int) {
        _cpuCores.value = cores
    }

    fun setSdCardSize(gb: Int) {
        _sdCardSizeGb.value = gb
    }

    fun setGpuRendering(gpu: String) {
        _gpuRendering.value = gpu
    }

    fun setDeviceName(name: String) {
        _deviceName.value = name
    }

    fun updateStorageInfo(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileDir = context.filesDir
            _freeStorageBytes.value = fileDir.usableSpace
            _totalStorageBytes.value = fileDir.totalSpace
        }
    }

    private fun addLog(tag: String, message: String, type: LogType) {
        val newEntry = LogEntry(
            timestamp = timeFormatter.format(Date()),
            tag = tag,
            message = message,
            type = type
        )
        // Keep logs size reasonable (e.g. up to 300 entries) to prevent memory bloating
        _logs.value = (_logs.value + newEntry).takeLast(300)
    }

    fun startCreation(context: Context) {
        if (_status.value == EmulatorStatus.CREATING) return

        _status.value = EmulatorStatus.CREATING
        addLog("CONSOLE", "Инициализация процесса создания эмулятора [${_deviceName.value}]", LogType.INFO)
        addLog("CONF", "Конфигурация: Ядра CPU: ${_cpuCores.value}, ОЗУ: ${_ramSizeGb.value}ГБ, Диск: ${_sdCardSizeGb.value}ГБ", LogType.INFO)
        addLog("CONF", "Графический движок: ${_gpuRendering.value}", LogType.DEBUG)

        workerJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _currentFileProgress.value = 0f
                _writeSpeedMbs.value = 0.0

                val parentFolder = File(context.filesDir, "emulator_system_partitions")
                if (!parentFolder.exists()) {
                    parentFolder.mkdirs()
                }

                val bufferSize = 1024 * 1024 // 1 MB buffer for high efficiency
                val buffer = ByteArray(bufferSize) { 0 } // Fill with zeros
                val targetFileSizeBytes = 200L * 1024 * 1024 // Exactly 200 MB

                while (_status.value == EmulatorStatus.CREATING) {
                    val fileIdx = _currentFileIndex.value
                    val fileName = "system_rom_part_${_deviceName.value.replace(Regex("[^a-zA-Z0-9]"), "_")}_idx_${fileIdx}.dat"
                    val file = File(parentFolder, fileName)

                    addLog("MIRROR", "Подключение к репозиторию системных образов...", LogType.INFO)
                    delay(300)
                    yield()

                    addLog("DOWNLOAD", "Начало загрузки и декомпиляции сегмента ROM модуля #$fileIdx (размер: 200.0 МБ) в $fileName...", LogType.INFO)

                    val fos = FileOutputStream(file)
                    var bytesWritten = 0L
                    var lastTime = System.currentTimeMillis()
                    var speedMeasureBytes = 0L

                    try {
                        while (bytesWritten < targetFileSizeBytes && _status.value == EmulatorStatus.CREATING) {
                            fos.write(buffer)
                            bytesWritten += bufferSize
                            _totalBytesWritten.value += bufferSize
                            speedMeasureBytes += bufferSize

                            // Calculate progress for current 200MB file
                            val progress = bytesWritten.toFloat() / targetFileSizeBytes
                            _currentFileProgress.value = progress

                            // Calculate speed every 400ms
                            val currentTime = System.currentTimeMillis()
                            val delta = currentTime - lastTime
                            if (delta >= 400) {
                                val speedMbs = (speedMeasureBytes / (1024.0 * 1024.0)) / (delta / 1000.0)
                                _writeSpeedMbs.value = speedMbs
                                speedMeasureBytes = 0L
                                lastTime = currentTime

                                // Occasionally output random tech logs
                                if (Random.nextInt(5) == 0) {
                                    val randomLog = techLogPool[Random.nextInt(techLogPool.size)]
                                    addLog("KERNEL", randomLog, LogType.DEBUG)
                                }
                                updateStorageInfo(context)
                            }
                            yield()
                        }
                    } catch (e: Exception) {
                        addLog("DISK_IO", "Ошибка файловой операции при записи: ${e.localizedMessage}", LogType.ERROR)
                        _status.value = EmulatorStatus.ERROR
                        try { fos.close() } catch (ignore: Exception) {}
                        throw e
                    } finally {
                        try {
                            fos.flush()
                            fos.close()
                        } catch (ignore: Exception) {}
                    }

                    if (_status.value == EmulatorStatus.CREATING) {
                        addLog("VERIFIER", "Сегмент ROM #$fileIdx проверен сигнатурой SHA-256. Статус: Успешно записан и разбит на сектора.", LogType.SUCCESS)
                        _currentFileIndex.value = _currentFileIndex.value + 1
                    }
                    delay(500) // Small pause between files
                }
            } catch (tracker: Exception) {
                addLog("CONSOLE", "Процесс приостановлен или завершен с ошибкой: ${tracker.localizedMessage}", LogType.WARNING)
            }
        }
    }

    fun pauseCreation() {
        if (_status.value == EmulatorStatus.CREATING) {
            _status.value = EmulatorStatus.PAUSED
            workerJob?.cancel()
            _writeSpeedMbs.value = 0.0
            addLog("SYSTEM", "Процесс сборки приостановлен пользователем. Выгрузка временных потоков ядра.", LogType.WARNING)
        }
    }

    fun resumeCreation(context: Context) {
        if (_status.value == EmulatorStatus.PAUSED) {
            startCreation(context)
        }
    }

    fun stopAndCleanup(context: Context) {
        _status.value = EmulatorStatus.IDLE
        workerJob?.cancel()
        _writeSpeedMbs.value = 0.0
        _currentFileProgress.value = 0f

        viewModelScope.launch(Dispatchers.IO) {
            addLog("CLEANUP", "Инициализация деаллокации дискового пространства...", LogType.WARNING)

            val parentFolder = File(context.filesDir, "emulator_system_partitions")
            if (parentFolder.exists()) {
                val files = parentFolder.listFiles()
                var deletedCount = 0
                var totalSpaceFreed = 0L
                files?.forEach { file ->
                    val len = file.length()
                    if (file.delete()) {
                        deletedCount++
                        totalSpaceFreed += len
                    }
                }
                val freMb = totalSpaceFreed / (1024L * 1024L)
                addLog("CLEANUP", "Удалено временных сегментов диска: $deletedCount. Освобождено памяти: $freMb МБ.", LogType.SUCCESS)
            }

            _currentFileIndex.value = 0
            _totalBytesWritten.value = 0L
            addLog("SYSTEM", "Память очищена. Сессия сборки эмулятора сброшена в исходное состояние.", LogType.INFO)
            updateStorageInfo(context)
        }
    }

    fun cleanAllCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val parentFolder = File(context.filesDir, "emulator_system_partitions")
            if (parentFolder.exists()) {
                val files = parentFolder.listFiles()
                files?.forEach { it.delete() }
            }
            _currentFileIndex.value = 0
            _totalBytesWritten.value = 0L
            _currentFileProgress.value = 0f
            addLog("CLEANUP", "Все загруженные сегменты системы были стерты.", LogType.SUCCESS)
            updateStorageInfo(context)
        }
    }
}
