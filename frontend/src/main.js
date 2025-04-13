import { createApp } from 'vue';
import App from './App.vue';
import './style.css';
import axios from 'axios';
import router from './router';

createApp(App)
    .use(router)
    .mount('#app');

// Configure Axios with retry logic
axios.interceptors.response.use(undefined, async (err) => {
    const { config, message } = err;

    // If the error contains ECONNABORTED or timeout exceeded, we retry
    if (!config || !config.retry || !(message.includes('timeout') || message.includes('ECONNABORTED'))) {
        return Promise.reject(err);
    }

    // Set the variable for tracking retry count
    config.retryCount = config.retryCount || 0;

    // Check if we've maxed out the total number of retries
    if (config.retryCount >= config.retry) {
        // Reject with the error
        return Promise.reject(err);
    }

    // Increase the retry count
    config.retryCount += 1;

    console.log(`Retrying request (${config.retryCount}/${config.retry}): ${config.url}`);

    // Create new promise to handle retry
    const backoff = new Promise((resolve) => {
        setTimeout(() => {
            console.log('Retrying after backoff...');
            resolve();
        }, config.retryDelay || 1000);
    });

    // Return the promise in which recalls axios to retry the request
    await backoff;
    return axios(config);
});

// Set default axios config for all requests
axios.defaults.timeout = 30000;
axios.defaults.retry = 2;
axios.defaults.retryDelay = 1000;
axios.defaults.headers.common['Cache-Control'] = 'no-cache';

// Create Vue app and mount
const app = createApp(App);
app.mount('#app'); 