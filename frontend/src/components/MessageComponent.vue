<template>
    <div>
      <h2>Message from Backend:</h2>
      <p v-if="loading">Loading...</p>
      <p v-else>{{ message }}</p>
    </div>
  </template>
  
  <script>
  import axios from "axios";
  
  export default {
    data() {
      return {
        message: "",
        loading: true
      };
    },
    mounted() {
      this.fetchMessage();
    },
    methods: {
      async fetchMessage() {
        try {
          const response = await axios.get("http://localhost:8080/api/message");
          this.message = response.data;
        } catch (error) {
          console.error("Error fetching message:", error);
          this.message = "Failed to fetch message.";
        } finally {
          this.loading = false;
        }
      }
    }
  };
  </script>
  
  <style scoped>
  h2 {
    color: #3498db;
  }
  </style>
  