import { createApp } from "vue";

//App.vue is root Vue component, managing the layout and structure of the app 
import App from "./App.vue"; //can write as import App from "@/App.vue", the @ directs to source directory (for vue cli) https://www.youtube.com/watch?v=PciUq6HcUNc

// Import Google API service
import googleApiService from "./services/google-api-service";

// Import router from the central router.js file
import router from "./router";

// Create and mount the app
createApp(App).use(router).mount("#app");
