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
        <component :is="feature.icon" class="feature-icon" />
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
          name: 'Crop',
          icon: 'i-lucide-crop'
        },
        {
          id: 'resize',
          name: 'Resize',
          icon: 'i-lucide-move-horizontal'
        },
        {
          id: 'layout',
          name: 'Photo Layout',
          icon: 'i-lucide-layout-grid'
        },
        {
          id: 'background-remove',
          name: 'Background Removal',
          icon: 'i-lucide-eraser'
        },
        {
          id: 'background-replace',
          name: 'Background Replace',
          icon: 'i-lucide-image'
        },
        {
          id: 'clothes',
          name: 'Clothes Replace',
          icon: 'i-lucide-shirt'
        },
        {
          id: 'face',
          name: 'Face Centering',
          icon: 'i-lucide-scan-face'
        },
        {
          id: 'enhance',
          name: 'Photo Enhancement',
          icon: 'i-lucide-sparkles'
        },
        {
          id: 'compliance-check',
          name: 'Compliance Check',
          icon: 'i-lucide-check-circle'
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
  width: 100px;
  background-color: #252a2e;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #3a3f45;
  overflow: hidden;
}

.feature-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px 0;
  overflow-y: auto;
}

.feature-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  background: transparent;
  border: none;
  color: #cccccc;
  cursor: pointer;
  text-align: center;
  transition: background-color 0.2s;
  border-left: 3px solid transparent;
}

.feature-btn:hover {
  background-color: #2a2e32;
  color: #ffffff;
}

.feature-btn.active {
  background-color: #2a2e32;
  color: #4a90e2;
  border-left-color: #4a90e2;
}

.feature-icon {
  width: 24px;
  height: 24px;
  margin-bottom: 6px;
}

.feature-name {
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>