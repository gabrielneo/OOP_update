const { contextBridge, ipcRenderer } = require("electron")

// Expose protected methods that allow the renderer process to use
// the ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld("electronAPI", {
  // Add any required API methods here
  getAppVersion: () => ipcRenderer.invoke("get-app-version"),
  // Add more methods as needed
})

