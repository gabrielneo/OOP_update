import { createApp } from "vue";

//App.vue is root Vue component, managing the layout and structure of the app 
import App from "./App.vue"; //can write as import App from "@/App.vue", the @ directs to source directory (for vue cli) https://www.youtube.com/watch?v=PciUq6HcUNc

// Import Google API service
import googleApiService from "./services/google-api-service";

// import MessageComponent from "./components/MessageComponent.vue";

import { createWebHistory, createRouter } from "vue-router";

import LandingPage from "./pages/LandingPage.vue";
import EditingPage from "./pages/EditingPage.vue";

const routes = [
    { path: "/EditingPage", component: EditingPage },
    { path: "/", component: LandingPage },
    // { path: "/profile", component: ProfilePage }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

createApp(App).use(router).mount("#app");
