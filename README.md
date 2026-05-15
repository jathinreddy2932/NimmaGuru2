<img width="340" height="763" alt="Screenshot 2026-05-06 102021" src="https://github.com/user-attachments/assets/7fb40b33-f8aa-4242-90ed-acb2ce7aa008" />
<img width="340" height="762" alt="Screenshot 2026-05-06 100739" src="https://github.com/user-attachments/assets/ccdee764-d22a-4695-862b-34e86660d980" />
<img width="344" height="761" alt="Screenshot 2026-05-06 100658" src="https://github.com/user-attachments/assets/3358056e-34fd-4658-a007-1a41a1073453" />
<img width="343" height="760" alt="Screenshot 2026-05-06 100634" src="https://github.com/user-attachments/assets/857b03f6-0844-4e01-b021-03a82b6d65e7" />
<img width="342" height="764" alt="Screenshot 2026-05-06 100621" src="https://github.com/user-attachments/assets/f0722f7c-b3f8-4d3b-bb45-5445d4dbb62a" />
<img width="338" height="758" alt="Screenshot 2026-05-06 100554" src="https://github.com/user-attachments/assets/3d5a5bf0-6be3-4784-9fae-bf130a822df0" />
<img width="345" height="764" alt="Screenshot 2026-05-06 100540" src="https://github.com/user-attachments/assets/098ab7da-e98f-48c3-b561-9723e99a460a" />
<img width="350" height="762" alt="Screenshot 2026-05-06 100455" src="https://github.com/user-attachments/assets/c620e7d7-cd38-46a1-ba44-fbae87171eb4" />
# Nimma-Guru

## Problem Statement
There is a growing disconnect between experienced retirees and youth who need mentorship, especially in rural areas. Retirees have vast knowledge but lack a platform to share it, while students often lack access to quality mentorship. Nimma-Guru bridges this gap by connecting teaching professionals and skilled retirees with students for skill sharing, localized learning, and valuable mentorship.

## Features
- **Role-Based Workflows**: Separate login and dashboards tailored for Mentors (Gurus) and Students.
- **Bilingual Interface**: Seamless switching between English and Kannada to ensure accessibility for rural users.
- **Session Management**: Mentors can easily schedule, edit, and manage their teaching sessions.
- **Wall of Fame & Gratitude**: Students can send "Thank You" notes, boosting mentors' visibility on the community Wall of Fame.
- **Accessible Design**: Utilizes a "Heritage Premium UI" with large fonts, deep teal/gold contrast, and intuitive navigation tailored specifically for elderly users.
- **Real-Time Data**: Fast and reliable synchronization using Firebase Firestore.

## Tech Stack
- **Frontend**: Kotlin, Android Jetpack Compose
- **Backend & Database**: Firebase Authentication, Firebase Cloud Firestore
- **Build System**: Gradle Kotlin DSL

## Installation Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/jathinreddy2932/NimmaGuru2.git
   ```
2. Open the project directory in **Android Studio**.
3. Allow Android Studio to sync the project with the Gradle files.
4. **Firebase Setup**: Ensure you configure your Firebase project and add the `google-services.json` file into the `app/` directory (Authentication and Firestore must be enabled).

## Run Command
- To build and run the application via Android Studio, click the **Run** button (Shift + F10) and deploy to a connected device or emulator.
- Alternatively, to build from the command line, use:
  ```bash
  ./gradlew assembleDebug
  ```

## Screenshots / Demo

### 1. Role Selection & Login Screen
*(Drag and drop your Role Selection and Login screenshots here)*

### 2. Mentor Dashboard & Wall of Fame
*(Drag and drop your Mentor Dashboard and Wall of Fame screenshots here)*

### 3. Host Class
*(Drag and drop your Host Class screenshot here)*

---
**Demo Video:** [Watch Project Demo Here](https://drive.google.com/file/d/1ulnooCxRu-j89-xKgQ0b--iVZuhAJsKN/view?usp=sharing)

## Folder Structure
```text
NimmaGuru2/
├── app/
│   ├── src/main/java/com/example/nimmaguru/   # Main application logic, UI components, and models
│   ├── src/main/res/                          # Resources (icons, strings, themes)
│   └── build.gradle.kts                       # App-level build configuration
├── build.gradle.kts                           # Project-level dependencies and plugins
├── settings.gradle.kts                        # Gradle workspace configuration
└── README.md                                  # Project documentation
```

## Future Improvements
- Expand language support for additional regional Indian languages.
- Integrate native in-app video calling for remote learning sessions.
- Add an AI-assisted skill-matching algorithm to better pair students with the right mentors.
