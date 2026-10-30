# ChapterSplit 📑✨

**ChapterSplit** is an intelligent Android application that divides large PDF documents into clean, chapter-wise PDF files according to the document's Table of Contents or Index. Powered by **Google Gemini AI**, **Android PdfRenderer**, and modern **Jetpack Compose**.

---

## 🌟 Key Features

1. **Flexible PDF Ingestion**
   - **Local Device Storage**: Choose any PDF using the system Storage Access Framework.
   - **Google Drive**: Import files directly from Google Drive or paste a Google Drive shared file link.
   - **Web PDF Download**: Direct download from any public PDF URL with download progress indicators.
   - **Index Photo Vision**: Snap or upload a photo of a physical book's Index / Table of Contents for automated OCR & chapter boundary extraction.
   - **Science Textbook Library**: Includes pre-loaded scientific textbooks (*Principles of Quantum Mechanics*, *Molecular Biology & Genetics*, *Algorithms & System Architecture*) for instant end-to-end testing.

2. **Gemini AI Document Intelligence**
   - **High-Thinking Reasoning (`gemini-3.1-pro-preview`)**: Employs `thinkingLevel = "HIGH"` to reason through Roman numeral page offsets, front/back matter distinction, and align printed index pages with real PDF page boundaries.
   - **Multimodal Visual Extraction (`gemini-3.1-pro-preview`)**: Scans photos of printed index pages and extracts chapter boundaries.
   - **Fast Auto-Segregation (`gemini-3.1-flash-lite`)**: Heuristic chapter boundary detector for instant segmentation.
   - **Chapter Summaries (`gemini-3.5-flash`)**: Generates 2-3 sentence chapter summaries and key topic lists on demand.

3. **Interactive Page & Chapter Previews**
   - Native Android `PdfRenderer` engine for crisp page rendering.
   - Interactive zoom & pan gesture support with zoom reset.
   - Chapter filtering tabs to quickly navigate between chapters.
   - Dynamic page coverage timeline showing coverage across the entire book.

4. **Multi-Chapter Splitting & ZIP Packaging**
   - Splits the document into individual, numbered chapter PDFs (e.g. `01_Preface.pdf`, `02_Wave_Duality.pdf`).
   - Automatically bundles all chapter PDFs into a clean `.zip` archive.
   - Includes a formatted `00_TABLE_OF_CONTENTS.txt` and a machine-readable `manifest.json`.
   - **Export to Local Device**: Save the `.zip` archive anywhere on device storage.
   - **Share & Cloud Export**: Open the Android system Sharesheet to save directly to Google Drive, send via email, or share with other apps.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.2+
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines & StateFlow
- **PDF Processing**: Native Android `PdfRenderer` and `PdfDocument`
- **Networking**: OkHttp 4 & Retrofit
- **Image Display**: Coil Compose
- **AI Engine**: Gemini API REST Client (`gemini-3.1-pro-preview`, `gemini-3.5-flash`, `gemini-3.1-flash-lite-preview`)
- **Packaging**: Standard Java `ZipOutputStream` & Android `FileProvider`

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Koala or newer
- JDK 17 or higher
- Android SDK with `minSdk = 24` and `targetSdk = 36`

### Setup & Configuration
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/ChapterSplit.git
   cd ChapterSplit
   ```

2. Configure your Gemini API Key:
   - Create a `.env` file in the project root:
     ```properties
     GEMINI_API_KEY=your_gemini_api_key_here
     ```
   - You can get a free Gemini API key from [Google AI Studio](https://aistudio.google.com/).

3. Build and Run:
   - Open the project in **Android Studio**.
   - Sync Gradle project files.
   - Run on an Android emulator (API 24+) or physical device.
   - Or build via command line:
     ```bash
     gradle assembleDebug
     ```

## 🔄 Automated CI/CD (GitHub Actions)

This repository includes a pre-configured **GitHub Actions** CI/CD pipeline (`.github/workflows/build.yml`) that builds the app automatically every time you push:

- **Automatic Trigger**: Triggers automatically on every `push` or `pull_request` to `main` / `master` (and can also be triggered manually from the "Actions" tab).
- **Automated Tasks**:
  1. Sets up JDK 17 and caches Gradle dependencies.
  2. Restores keystore credentials and configures environment secrets.
  3. Builds the Android APK (`./gradlew assembleDebug`).
  4. Runs unit tests to ensure stability.
  5. Publishes the compiled APK as a downloadable GitHub artifact (`ChapterSplit-debug-apk`).

### 📦 How to Download the Built APK from GitHub
1. Navigate to your repository on GitHub.
2. Click the **Actions** tab at the top.
3. Click on the latest workflow run (e.g., `Build Android App`).
4. Scroll down to the **Artifacts** section at the bottom of the page.
5. Click **`ChapterSplit-debug-apk`** to download the ready-to-install Android APK!

### 🔑 Setting up your Gemini API Key in GitHub (Optional)
To have GitHub Actions build with your custom Gemini API key:
1. Go to your GitHub repository **Settings** > **Secrets and variables** > **Actions**.
2. Click **New repository secret**.
3. Name: `GEMINI_API_KEY`, Value: your API key from Google AI Studio.

---

## 📄 License
This project is open-source and available under the Apache 2.0 License.
