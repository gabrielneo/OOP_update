const { app, BrowserWindow, Menu } = require("electron");
const path = require("path");

let mainWindow;

app.whenReady().then(() => {
    mainWindow = new BrowserWindow({
        width: 800,
        height: 600,
        webPreferences: {
            contextIsolation: true,
            enableRemoteModule: false,
            preload: path.join(__dirname, "preload.js"), // Add preload script
            nodeIntegration: true,
            zoomFactor:1.0
        }
    });

    if (process.env.NODE_ENV === "development") {
        mainWindow.loadURL("http://localhost:5173"); // Load Vite Dev Server (this is for development)
        //for development, Vite starts a dev server (localhost:5173).
        //lets us handle hot-reloading
    } else {
        mainWindow.loadFile(path.join(__dirname, "dist", "index.html")); // Load built files (FOR PRODUCTION)
    } //Instead of running a dev server, we use Vite’s build system to generate static files (dist/ folder).
    //Electron should then load the built static files.

    mainWindow.on("closed", () => {
        mainWindow = null;
    });

    // Remove the default menu
    Menu.setApplicationMenu(null);

});

app.on("window-all-closed", () => {
    if (process.platform !== "darwin") {
        app.quit();
    }
});
