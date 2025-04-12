<template>
  <div v-if="isVisible" class="compliance-modal">
    <div class="compliance-modal-content">
      <div class="compliance-modal-header">
        <h3>Image Compliance Check</h3>
        <button class="close-btn" @click="close">×</button>
      </div>
      
      <div class="compliance-modal-body">
        <div v-if="loading" class="loading-indicator">
          <div class="spinner"></div>
          <p>Checking image compliance...</p>
        </div>
        
        <div v-else class="compliance-results">
          <div class="compliance-status" :class="{ 'compliant': result.compliant, 'non-compliant': !result.compliant }">
            <div class="status-icon">
              <span v-if="result.compliant">✓</span>
              <span v-else>!</span>
            </div>
            <h4>{{ result.message }}</h4>
          </div>
          
          <div v-if="!result.compliant && result.issues && result.issues.length > 0" class="issues-list">
            <h5>The following issues were found:</h5>
            <ul>
              <li v-for="(issue, index) in result.issues" :key="index">{{ issue }}</li>
            </ul>
          </div>
        </div>
      </div>
      
      <div class="compliance-modal-footer">
        <button class="action-btn" @click="close">Close</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ComplianceCheckModal',
  props: {
    isVisible: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    },
    result: {
      type: Object,
      default: () => ({
        compliant: false,
        issues: [],
        message: 'Compliance check not performed yet'
      })
    }
  },
  methods: {
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>
.compliance-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
}

.compliance-modal-content {
  background-color: white;
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
  border: 2px solid #e74c3c;
}

.compliance-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #eee;
}

.compliance-modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: #e74c3c;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
}

.compliance-modal-body {
  padding: 20px;
  overflow-y: auto;
}

.compliance-modal-footer {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #eee;
}

.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.spinner {
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  width: 30px;
  height: 30px;
  animation: spin 1s linear infinite;
  margin-bottom: 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.compliance-status {
  display: flex;
  align-items: center;
  padding: 15px;
  border-radius: 6px;
  margin-bottom: 20px;
}

.compliant {
  background-color: rgba(46, 204, 113, 0.1);
  border: 1px solid rgba(46, 204, 113, 0.3);
}

.non-compliant {
  background-color: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.3);
}

.status-icon {
  margin-right: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  font-size: 18px;
  font-weight: bold;
  color: #e74c3c;
}

.compliant .status-icon {
  background-color: rgba(46, 204, 113, 0.2);
  color: #27ae60;
}

.non-compliant .status-icon {
  background-color: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
}

.compliance-status h4 {
  margin: 0;
  font-size: 16px;
  color: #e74c3c;
}

.issues-list {
  margin-top: 20px;
}

.issues-list h5 {
  font-size: 14px;
  margin-bottom: 10px;
  color: #e74c3c;
}

.issues-list ul {
  margin: 0;
  padding-left: 0;
  list-style-type: none;
}

.issues-list li {
  margin-bottom: 12px;
  font-size: 15px;
  color: #e74c3c;
  background-color: rgba(231, 76, 60, 0.1);
  border: 1px solid rgba(231, 76, 60, 0.3);
  padding: 12px 16px;
  border-radius: 4px;
  list-style-position: inside;
  font-weight: 500;
}

.action-btn {
  background-color: #e74c3c;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background-color 0.2s;
}

.action-btn:hover {
  background-color: #c0392b;
}
</style> 