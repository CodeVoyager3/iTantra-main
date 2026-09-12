package com.itantra.app.model

import java.util.Locale

/**
 * 10 Official Regional & National Languages for iTantra
 * with native names, glyph avatar initials, and on-device model package size.
 */
enum class SupportedLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val nativeInitial: String,
    val downloadSizeMb: Int,
    val locale: Locale,
    val sampleAlertPhrase: String
) {
    HINDI(
        code = "hi",
        englishName = "HINDI",
        nativeName = "हिन्दी",
        nativeInitial = "हिं",
        downloadSizeMb = 292,
        locale = Locale("hi", "IN"),
        sampleAlertPhrase = "सावधान: चक्रवात चेतावनी। तुरंत सुरक्षित स्थान पर जाएं।"
    ),
    ENGLISH(
        code = "en",
        englishName = "ENGLISH",
        nativeName = "English",
        nativeInitial = "EN",
        downloadSizeMb = 195,
        locale = Locale("en", "IN"),
        sampleAlertPhrase = "Priority Alert: Disaster Response Protocol Activated."
    ),
    TAMIL(
        code = "ta",
        englishName = "TAMIL",
        nativeName = "தமிழ்",
        nativeInitial = "த",
        downloadSizeMb = 292,
        locale = Locale("ta", "IN"),
        sampleAlertPhrase = "எச்சரிக்கை: பேரிடர் மீட்பு குழு விரைந்துள்ளது."
    ),
    BENGALI(
        code = "bn",
        englishName = "BENGALI",
        nativeName = "বাংলা",
        nativeInitial = "বা",
        downloadSizeMb = 292,
        locale = Locale("bn", "IN"),
        sampleAlertPhrase = "সতর্কতা: জরুরি সংকেত জারি করা হয়েছে।"
    ),
    MARATHI(
        code = "mr",
        englishName = "MARATHI",
        nativeName = "मराठी",
        nativeInitial = "म",
        downloadSizeMb = 292,
        locale = Locale("mr", "IN"),
        sampleAlertPhrase = "सतर्कता: आपत्कालीन मदत पथक रवाना झाले आहे."
    ),
    TELUGU(
        code = "te",
        englishName = "TELUGU",
        nativeName = "తెలుగు",
        nativeInitial = "తె",
        downloadSizeMb = 292,
        locale = Locale("te", "IN"),
        sampleAlertPhrase = "హెచ్చరిక: అత్యవసర సహాయ కేంద్రం అప్రమత్తమైంది."
    ),
    GUJARATI(
        code = "gu",
        englishName = "GUJARATI",
        nativeName = "ગુજરાતી",
        nativeInitial = "ગુ",
        downloadSizeMb = 292,
        locale = Locale("gu", "IN"),
        sampleAlertPhrase = "ચેતવણી: તાકીદની સ્થળાંતર સૂચના જાહેર કરાઈ છે."
    ),
    KANNADA(
        code = "kn",
        englishName = "KANNADA",
        nativeName = "ಕನ್ನಡ",
        nativeInitial = "ಕ",
        downloadSizeMb = 292,
        locale = Locale("kn", "IN"),
        sampleAlertPhrase = "ಎಚ್ಚರಿಕೆ: ವಿಪತ್ತು ನಿರ್ವಹಣಾ ತಂಡ ಸನ್ನದ್ಧವಾಗಿದೆ."
    ),
    MALAYALAM(
        code = "ml",
        englishName = "MALAYALAM",
        nativeName = "മലയാളം",
        nativeInitial = "മ",
        downloadSizeMb = 292,
        locale = Locale("ml", "IN"),
        sampleAlertPhrase = "ജാഗ്രത: അടിയന്തര ദുരന്ത നിവാരണ മുന്നറിയിപ്പ്."
    ),
    ODIA(
        code = "or",
        englishName = "ODIA",
        nativeName = "ଓଡ଼ିଆ",
        nativeInitial = "ଓ",
        downloadSizeMb = 292,
        locale = Locale("or", "IN"),
        sampleAlertPhrase = "ସତର୍କତା: ଉପକୂଳବର୍ତ୍ତୀ ଅଞ୍ଚଳ ଖାଲି କରିବାକୁ ନିର୍ଦ୍ଦେଶ।"
    );

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HINDI
        }
    }
}

enum class WalkieTalkieMode {
    PTT,        // Push-to-Talk: Hold to talk
    CONTINUOUS  // Hands-free VAD voice detection
}

enum class RadioChannelState {
    STANDBY,       // Idle, listening for peer packets
    LISTENING,     // Mic active, capturing user voice
    TRANSMITTING,  // Broadcasting packet over mesh
    RECEIVING      // Receiving stream from peer
}

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    PAIRING,
    CONNECTED,
    LOST
}

enum class TransportProtocol {
    WIFI_DIRECT,
    BLUETOOTH,
    BLE
}

enum class AlertPriority {
    ROUTINE,
    URGENT,
    CRITICAL_DISTRESS
}

enum class VadStatus {
    SILENCE,
    SPEECH_DETECTED
}

data class PeerDevice(
    val id: String,
    val name: String,
    val address: String,
    val protocol: TransportProtocol,
    val signalStrengthDbm: Int = -55,
    val isConnected: Boolean = false,
    val isP2pHost: Boolean = false,
    val port: Int = 8889,
    val batteryPercent: Int = 100
)

enum class RescueConnectionMode {
    STANDBY,        // Scanning nearby mesh; no active voice link
    ONE_TO_ONE,     // Active 2-way voice intercom with a selected victim
    BROADCAST_ALL   // 1-way emergency broadcast streaming to all victims
}

data class DistressVictim(
    val id: String,
    val callsign: String,
    val distanceMeters: Int,
    val signalDbm: Int,
    val language: SupportedLanguage,
    val batteryPercent: Int,
    val activeMinutes: Int,
    val distressMessage: String,
    val isIntercomConnected: Boolean = false,
    val relativeBearingDegrees: Float = 0f, // 0° = North, 90° = East, 180° = South, 270° = West
    val hazardType: String = "Structural Collapse",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nodeId: Long = 0L // mesh node id of the beacon this victim was derived from
)

data class RescuerNode(
    val id: String,
    val callsign: String,
    val distanceMeters: Int,
    val signalDbm: Int,
    val role: String = "NDRF Search & Rescue",
    val isConnected: Boolean = false
)

data class MissionTelemetry(
    val nodeCallsign: String = "ITANTRA-NODE-01",
    val peerCallsign: String = "ITANTRA-BASE-ALPHA",
    val frequencyGhz: String = "5.180 GHz (Ch 36)",
    val signalDbm: Int = -52,
    val linkQualityPercent: Int = 96,
    val latencyMs: Int = 12,
    val batteryPercent: Int = 88,
    val powerDrawWatts: Float = 0.42f,
    val isModelLoaded: Boolean = true,
    val batteryTemperatureC: Float = 30.5f,
    val isCharging: Boolean = false,
    val localIpAddress: String = "192.168.49.1",
    val deviceModel: String = "Tactical Terminal",
    val ramUsageMb: Float = 48.0f,
    val deviceRole: String = "TRANSCEIVER"
)

// UI models for Model Hub and Settings
data class SttModelAsset(
    val id: String = "conformer-int8",
    val name: String = "AI4Bharat IndicConformer INT8",
    val architecture: String = "conformer_ctc",
    val sourceModel: String = "ai4bharat/indicconformer",
    val onnxExport: String = "model.int8.onnx",
    val modelType: String = "INT8",
    val modelPath: String = "stt/model.int8.onnx",
    val tokensPath: String = "stt/tokens.txt",
    val sampleRateHz: Int = 16000,
    val featureDim: Int = 80
)

data class TtsModelAsset(
    val id: String = "fastpitch-hifigan",
    val name: String = "FastPitch + HiFi-GAN ONNX",
    val acousticModelPath: String = "tts/fastpitch.onnx",
    val vocoderPath: String = "tts/hifigan.onnx",
    val frontendConfigPath: String = "tts/fastpitch_config.json",
    val sampleRateHz: Int = 22050
)

data class LanguagePack(
    val code: String,
    val englishName: String,
    val stt: SttModelAsset? = SttModelAsset(),
    val tts: TtsModelAsset? = TtsModelAsset()
)

data class VerifiedAsset(
    val path: String,
    val sizeBytes: Long,
    val sha256: String
)

data class SttModelInfo(
    val name: String = "AI4Bharat IndicConformer INT8",
    val runtime: String = "ONNX Runtime Mobile (INT8)",
    val modelSizeMb: Float = 64.5f,
    val isQuantized: Boolean = true,
    val isLoaded: Boolean = true,
    val inferenceLatencyMs: Int = 85
)

data class TtsModelInfo(
    val name: String = "FastPitch + HiFi-GAN (FP16)",
    val runtime: String = "ONNX Runtime Mobile",
    val modelSizeMb: Float = 130.4f,
    val sampleRateHz: Int = 22050,
    val isReady: Boolean = true
)

enum class AiModelCategory(val label: String) {
    STT("STT SPEECH"),
    TTS("TTS VOICE"),
    TRANSLATION("NMT TRANSLATE")
}

data class DownloadedAiModel(
    val id: String,
    val name: String,
    val category: AiModelCategory,
    val language: String,
    val sizeMb: Int,
    val isSystemCore: Boolean = false,
    val version: String = "v1.2",
    val description: String = "100% On-device offline neural inference"
)
