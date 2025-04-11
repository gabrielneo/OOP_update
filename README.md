# Google Drive Integration App

This application demonstrates integration with Google Drive API, allowing users to browse, edit, and save images to their Google Drive account.

## Features

- Google OAuth authentication for any user
- Browse and manage your Google Drive files
- Open, edit, and save images
- Apply various image filters
- Save images locally or to Google Drive

## Prerequisites

- JDK 11 or higher
- Maven
- Node.js and npm
- Google Cloud Console project with Google Drive API enabled

## Setup Instructions

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google Drive API:
   - Navigate to "APIs & Services" > "Library"
   - Search for "Google Drive API" and enable it
4. Create OAuth 2.0 credentials:
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Web application" as the application type
   - Add authorized JavaScript origins:
     - `http://localhost:3000`
     - `http://localhost:5173`
     - `http://localhost:8080`
   - Add authorized redirect URIs:
     - `http://localhost:8080/auth/google/callback`
     - `http://localhost:8888/Callback`
   - Click "Create"
5. Download the credentials JSON file and save it as `credential.json` in `backend/src/main/resources/`

### 2. Environment Setup

Create a `.env` file in the root directory with the following content (replace with your credentials):

```
GOOGLE_CLIENT_ID=your_client_id
GOOGLE_CLIENT_SECRET=your_client_secret
GOOGLE_REDIRECT_URI=http://localhost:8080/auth/google/callback
```

### 3. Build and Run

#### Install Backend Dependencies

```bash
cd backend
mvn clean install
```

#### Install Frontend Dependencies

```bash
cd frontend
npm install
```

#### Run the Application

Use the provided PowerShell script to start all components:

```bash
./run-app.ps1
```

This will start:
- Spring Boot backend server (port 8080)
- Vue.js frontend development server (port 5173 or 3000)
- Electron application

## Usage

1. The Electron app will open automatically
2. Click "Login with Google" to authenticate
3. After authentication, you can:
   - Browse your Google Drive files
   - Open images for editing
   - Apply filters and edits
   - Save back to Google Drive or locally

## Architecture

- **Backend**: Spring Boot application that handles Google Drive API integration
- **Frontend**: Vue.js application with Electron for desktop capabilities

## Troubleshooting

- If you encounter authentication issues, check that the redirect URIs in Google Cloud Console match those in your application
- For file-related errors, ensure you have the proper permissions for the target files in Google Drive
- Make sure all services are running properly (backend, frontend, and Electron)

## Project Structure

```
/project-root
├── /frontend        → Frontend (Electron.js + Vue.js)
│   ├── /src
│       ├── /components      → Vue components
│       ├── /pages           → Vue pages
│   ├── electron-main.cjs    → Electron main process
│   ├── index.html           → Main UI
│   ├── package.json         → Dependencies & scripts
├── /backend         → Backend (Spring Boot)
    ├── /src/main/java/com/example
    │   ├── /controller      → REST controllers
    │   ├── /service         → Business logic services
    │   ├── /model           → Data models
    ├── /src/main/resources
    │   ├── application.properties
    │   ├── credential.json  → Google API credentials
    ├── pom.xml              → Dependencies
```

## Key Java OOP Concepts Used

### 1. Encapsulation
- All model classes use private fields with getter/setter methods
- Implementation details are hidden from clients

### 2. Inheritance
- Spring's class hierarchy is leveraged (e.g., extending base controller classes)

### 3. Polymorphism
- File uploads support different input types (MultipartFile, InputStream)
- Method overloading provides different ways to call the same operation

### 4. Abstraction
- Service interfaces define operations without implementation details
- Controller only depends on service interfaces, not concrete implementations

### 5. Design Patterns
- **Dependency Injection**: Spring autowires components
- **Builder Pattern**: Used in Google Drive API calls
- **DTO Pattern**: DriveFile is a Data Transfer Object

### 6. SOLID Principles
- **Single Responsibility**: Each class has one purpose
- **Open/Closed**: Extensions possible without modifying existing code
- **Liskov Substitution**: Subtypes can be used in place of parent types
- **Interface Segregation**: Clients only depend on methods they use
- **Dependency Inversion**: High-level components depend on abstractions

## API Reference

The backend exposes these REST endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/drive/files` | GET | List Drive files/folders |
| `/api/drive/upload` | POST | Upload a file to Drive |
| `/api/drive/files/{fileId}` | PUT | Update a file in Drive |
| `/api/drive/folders` | POST | Create a new folder |
| `/api/drive/status` | GET | Check Drive connection |

## Security Notes

- OAuth 2.0 is used for secure authentication
- Refresh tokens are stored locally
- Never commit credential.json to version control 