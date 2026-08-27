package com.example.data

enum class VoiceState(val label: String) {
    IDLE("Ready to speak"),
    RECORDING("Listening..."),
    SENDING("Processing audio..."),
    THINKING("Generating response..."),
    SPEAKING("Assistant speaking...")
}
