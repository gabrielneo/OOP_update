<template>
  <div class="feature-panel">
    <div class="feature-list">
      <button 
        v-for="feature in features" 
        :key="feature.id"
        :class="['feature-btn', { active: activeFeature === feature.id }]"
        @click="selectFeature(feature.id)"
        :title="feature.name"
      >
        <div class="feature-icon">
          <!-- Crop icon -->
          <svg v-if="feature.id === 'crop'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6.13 1 6 16a2 2 0 0 0 2 2h15"/><path d="M1 6.13 16 6a2 2 0 0 1 2 2v15"/></svg>
          
          <!-- Resize icon -->
          <svg v-if="feature.id === 'resize'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 3h6v6"/><path d="M9 21H3v-6"/><path d="m21 3-7 7"/><path d="m3 21 7-7"/></svg>
          
          <!-- Remove Background icon -->
          <svg v-if="feature.id === 'background-remove'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="4" y="4" width="16" height="16" rx="2"/>
            <path d="m4 20 16-16"/>
            <path d="M15 5h4"/>
            <path d="M19 5v4"/>
          </svg>
          
          <!-- Background Replace icon -->
          <svg v-if="feature.id === 'background-replace'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
          
          <!-- Clothes Replace icon -->
          <svg v-if="feature.id === 'clothes'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.38 3.46 16 2a4 4 0 0 1-8 0L3.62 3.46a2 2 0 0 0-1.34 2.23l.58 3.47a1 1 0 0 0 .99.84H6v10c0 1.1.9 2 2 2h8a2 2 0 0 0 2-2V10h2.15a1 1 0 0 0 .99-.84l.58-3.47a2 2 0 0 0-1.34-2.23"/></svg>
          
          <!-- Face Centering icon -->
          <svg v-if="feature.id === 'face'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          
          <!-- Enhance Photo icon -->
          <svg v-if="feature.id === 'enhance'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/><path d="M5 3v4"/><path d="M19 17v4"/><path d="M3 5h4"/><path d="M17 19h4"/></svg>
          
          <!-- Photo Layout icon -->
          <svg v-if="feature.id === 'layout'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/></svg>
          
          <!-- Compliance Check icon -->
          <svg v-if="feature.id === 'compliance-check'" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        </div>
        <span class="feature-name">{{ feature.name }}</span>
      </button>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    activeFeature: {
      type: String,
      default: null
    }
  },
  data() {
    return {
      features: [
        {
          id: 'crop',
          name: 'Crop'
        },
        {
          id: 'resize',
          name: 'Resize'
        },
        {
          id: 'background-remove',
          name: 'Remove Background'
        },
        {
          id: 'background-replace',
          name: 'Replace Background'
        },
        {
          id: 'clothes',
          name: 'Replace Clothes'
        },
        {
          id: 'face',
          name: 'Face Centering'
        },
        {
          id: 'enhance',
          name: 'Enhance Photo'
        },
        {
          id: 'layout',
          name: 'Photo Layout'
        },
        {
          id: 'compliance-check',
          name: 'Compliance Check'
        }
      ]
    };
  },
  methods: {
    selectFeature(featureId) {
      this.$emit('feature-selected', featureId);
    }
  }
}
</script>

<style scoped>
.feature-panel {
  width: 100%;
  background-color: #252a2e;
  display: flex;
  border-bottom: 1px solid #3a3f45;
  overflow-x: auto;
}

.feature-list {
  display: flex;
  flex-direction: row;
  padding: 0;
  width: 100%;
}

.feature-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px 12px;
  background: transparent;
  border: none;
  color: #cccccc;
  cursor: pointer;
  text-align: center;
  transition: background-color 0.2s, color 0.2s;
  border-bottom: 3px solid transparent;
  flex: 1;
  min-width: fit-content;
}

.feature-btn:hover {
  background-color: #2a2e32;
  color: #ffffff;
}

.feature-btn.active {
  background-color: #2a2e32;
  color: #4a90e2;
  border-bottom-color: #4a90e2;
}

.feature-icon {
  width: 22px;
  height: 22px;
  margin-bottom: 6px;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-btn.active .feature-icon {
  color: #4a90e2;
}

.feature-name {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .feature-btn {
    padding: 8px 6px;
  }
  
  .feature-name {
    font-size: 10px;
  }
  
  .feature-icon {
    width: 18px;
    height: 18px;
    margin-bottom: 4px;
  }
  
  .feature-icon svg {
    width: 18px;
    height: 18px;
  }
}
</style>