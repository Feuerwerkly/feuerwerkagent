<template>
  <div class="im-avatar" :style="{ background: color }">
    <span v-if="emoji">{{ emoji }}</span>
    <span v-else class="letter">{{ letter }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { avatarColor } from '../../im/util'

const props = defineProps({
  name: { type: String, default: '' },
  id: { type: [Number, String], default: 0 },
  emoji: { type: String, default: '' }
})

const color = computed(() => avatarColor(props.id))
const letter = computed(() => (props.name || '?').slice(0, 1).toUpperCase())
</script>

<style scoped>
.im-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.letter {
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  user-select: none;
}

.im-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
