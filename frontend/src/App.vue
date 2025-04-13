<template>
  <div id="app">
    <RouterView></RouterView>

    <!-- Add Google Drive component -->
    <GoogleDriveComponent
      v-if="showDriveComponent"
      @file-uploaded="handleFileUploaded"
    />
  </div>
</template>

<script>
import GoogleDriveComponent from "./components/GoogleDriveComponent.vue";

export default {
  name: "App",
  components: {
    GoogleDriveComponent,
  },
  data() {
    return {
      showDriveComponent: false,
    };
  },
  methods: {
    handleFileUploaded(fileData) {
      // Handle the uploaded file data
      console.log("File uploaded to Drive:", fileData);
    },

    toggleDriveComponent() {
      this.showDriveComponent = !this.showDriveComponent;
    },
  },
};
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin: 0;
  padding: 0;
  min-width: 1050px; /* Increased from 960px to accommodate button text */
}

html, body {
  margin: 0;
  padding: 0;
  overflow: hidden;
  height: 100%;
  min-width: 1050px; /* Increased from 960px to match app container */
}
</style>
