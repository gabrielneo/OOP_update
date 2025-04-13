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
    name: 'LandingPage',
    component: LandingPage
  },
  {
    path: '/drive',
    name: 'DriveExplorer',
    component: DriveExplorerPage
  },
  {
    path: '/edit',
    name: 'EditingPage',
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
  history: createWebHistory(process.env.BASE_URL),
  routes
});

export default router; 