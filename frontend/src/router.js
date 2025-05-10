import { createRouter, createWebHistory } from 'vue-router';

// Import pages
import HomePage from './pages/HomePage.vue';
import DriveExplorerPage from './pages/DriveExplorerPage.vue';
import EditingPage from './pages/EditingPage.vue';
import BatchEditingPage from './pages/BatchEditingPage.vue';

// Route definitions
const routes = [
  {
    path: '/',
    name: 'EditingPage',
    component: EditingPage
  },
  {
    path: '/home',
    name: 'Home',
    component: HomePage
  },
  {
    path: '/drive',
    name: 'DriveExplorer',
    component: DriveExplorerPage
  },
  {
    path: '/edit/:fileId?',
    name: 'EditingPageWithParam',
    component: EditingPage,
    props: true
  },
  {
    path: '/batch-edit',
    name: 'BatchEditingPage',
    component: BatchEditingPage
  }
];

// Create router instance
const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router; 