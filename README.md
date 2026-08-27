# LingoBubble 🫧

LingoBubble is a modern Android application designed for real-time system audio translation. It captures internal audio from your device and provides live subtitles in a floating, highly customizable overlay. Perfect for watching YouTube videos, attending meetings, or watching foreign content without native subtitle support.

## ✨ Features

- **🎙️ Real-Time Translation:** Capture system audio directly and translate it on the fly.
- **🚀 Multiple Engines:**
    - **Azure Speech Service:** Ultra-fast, live streaming translation.
    - **Groq + Whisper:** High-precision, block-based translation using Whisper large-v3.
    - **Offline Mode:** Local Whisper (TFLite) support for private, no-latency use.
- **🎨 Material 3 Dashboard:** A beautiful, responsive setup screen with Material You dynamic coloring.
- **🫧 Floating Subtitles:**
    - **Movable Overlay:** Drag and drop the subtitle bar anywhere on the screen.
    - **Minimized Mode:** Collapse the bar into a tiny, pulsing bubble when you need more screen space.
    - **Customizable Appearance:** Adjust font size, background transparency, and text shadows for maximum readability.
- **📊 Audio Visualizer:** Live waveform icon that confirms the app is "hearing" your audio.
- **⚡ Quick Start Tile:** Start or stop LingoBubble directly from your Android Quick Settings panel.
- **🔒 Privacy First (BYOK):** "Bring Your Own Key" model. Your API keys are stored locally on your device and are never shared.

## 🛠️ Setup & Security

LingoBubble uses a **Bring Your Own Key** model to ensure your privacy and give you full control over your usage.

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/YOUR_USERNAME/LingoBubble.git
    ```
2.  **Open in Android Studio:** Open the project and let Gradle sync.
3.  **Obtain API Keys:**
    - **Azure:** Sign up for [Azure Speech Services](https://azure.microsoft.com/en-us/products/ai-services/ai-speech) and get your Key and Region.
    - **Groq:** Get an API key from [Groq Cloud](https://console.groq.com/).
4.  **Enter Keys in App:** Launch LingoBubble and enter your credentials in the **API Credentials** section.

## 📱 Permissions Required

- **Audio Recording:** To capture internal system audio.
- **Display Over Other Apps:** To show the floating subtitle overlay.
- **Media Projection:** To access the system audio stream (requested on every start).

## 🤝 Contributing

Contributions are welcome! If you have suggestions for new features or improvements, please open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
