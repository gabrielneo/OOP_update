const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

// Set the node option to handle uncaught exceptions properly
app.commandLine.appendSwitch('force-node-api-uncaught-exceptions-policy', 'true');

// Enable more detailed logging
console.log('Electron main process starting...');
console.log('Working directory:', process.cwd());

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      contextIsolation: true,
      enableRemoteModule: false,
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: true,
      zoomFactor: 1.0
    }
  });

  // Force development mode for debugging
  const isDev = true; // Use development mode
  
  console.log('Running in mode:', isDev ? 'development' : 'production');
  
  // Determine whether to load dev server or production build
  if (isDev) {
    const devServerUrl = 'http://localhost:5173';
    console.log('Loading from dev server:', devServerUrl);
    mainWindow.loadURL(devServerUrl);
    mainWindow.webContents.openDevTools(); // Open DevTools in development
  } else {
    // Try loading the minimal test page first
    const minimalPath = path.join(__dirname, 'minimal.html');
    console.log('Checking for minimal test page:', minimalPath);
    
    if (fs.existsSync(minimalPath)) {
      console.log('Loading minimal test page');
      mainWindow.loadFile(minimalPath);
    } else {
      // Fall back to regular index.html
      const indexPath = path.join(__dirname, 'dist', 'index.html');
      console.log('Loading from file:', indexPath);
      console.log('File exists:', fs.existsSync(indexPath));
      mainWindow.loadFile(indexPath);
    }
    
    // Always open DevTools in this version for debugging
    mainWindow.webContents.openDevTools();
  }

  mainWindow.webContents.on('did-finish-load', () => {
    console.log('Page loaded successfully');
  });

  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription) => {
    console.error('Failed to load page:', errorCode, errorDescription);
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Remove default menu in production
  if (!isDev) {
    Menu.setApplicationMenu(null);
  }
}

app.whenReady().then(() => {
  console.log('Electron app ready');
  createWindow();

  app.on('activate', () => {
    if (mainWindow === null) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// IPC Handlers for file operations
ipcMain.handle('get-app-version', () => {
  return app.getVersion();
});

// Handle file saving
ipcMain.handle('save-image', async (event, { buffer, fileName, defaultPath }) => {
  try {
    const { canceled, filePath } = await dialog.showSaveDialog({
      defaultPath: defaultPath || fileName,
      filters: [
        { name: 'Images', extensions: ['jpg', 'jpeg', 'png'] }
      ]
    });

    if (!canceled && filePath) {
      fs.writeFileSync(filePath, Buffer.from(buffer));
      return { success: true, filePath };
    }
    return { success: false, reason: 'User canceled' };
  } catch (error) {
    return { success: false, reason: error.message };
  }
});

// Handle file opening
ipcMain.handle('open-image', async () => {
  try {
    const { canceled, filePaths } = await dialog.showOpenDialog({
      properties: ['openFile'],
      filters: [
        { name: 'Images', extensions: ['jpg', 'jpeg', 'png'] }
      ]
    });

    if (!canceled && filePaths.length > 0) {
      const filePath = filePaths[0];
      const fileName = path.basename(filePath);
      const buffer = fs.readFileSync(filePath);
      return {
        success: true,
        fileName,
        filePath,
        buffer: buffer.toString('base64')
      };
    }
    return { success: false, reason: 'User canceled' };
  } catch (error) {
    return { success: false, reason: error.message };
  }
}); 