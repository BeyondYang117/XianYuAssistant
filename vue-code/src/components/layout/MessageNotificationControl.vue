<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import IconBell from '@/components/icons/IconBell.vue'
import { useMessageNotifications } from '@/composables/useMessageNotifications'

const router = useRouter()
const panelOpen = ref(false)
const {
  unreadCount, preferences, notificationPermission,
  requestDesktopPermission, savePreferences, refreshUnread
} = useMessageNotifications()

const toggleDesktopNotifications = async () => {
  if (!preferences.value.desktop) {
    await requestDesktopPermission()
  } else {
    savePreferences({ ...preferences.value, desktop: false })
  }
}

const toggleMessageNotifications = () => {
  const enabled = !preferences.value.enabled
  savePreferences({ ...preferences.value, enabled })
  if (enabled) refreshUnread(true)
}
</script>

<template>
  <div class="message-notification-control">
    <button class="message-notification-button" :class="{ 'has-unread': unreadCount }" title="消息通知" @click="panelOpen = !panelOpen">
      <IconBell />
      <span v-if="unreadCount" class="message-notification-count">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>
    <div v-if="panelOpen" class="message-notification-panel">
      <div class="message-notification-panel__header"><strong>消息提醒</strong><button title="关闭" @click="panelOpen = false">×</button></div>
      <p>买家新消息会在后台持续检查，并在这里显示未读数。</p>
      <label><input type="checkbox" :checked="preferences.enabled" @change="toggleMessageNotifications"><span>买家消息提醒</span></label>
      <label><input type="checkbox" :checked="preferences.titleFlash" :disabled="!preferences.enabled" @change="savePreferences({ ...preferences, titleFlash: !preferences.titleFlash })"><span>浏览器标题闪烁</span></label>
      <label><input type="checkbox" :checked="preferences.sound" :disabled="!preferences.enabled" @change="savePreferences({ ...preferences, sound: !preferences.sound })"><span>提示音</span></label>
      <label><input type="checkbox" :checked="preferences.desktop" :disabled="!preferences.enabled" @change="toggleDesktopNotifications"><span>桌面通知</span><small>{{ notificationPermission === 'denied' ? '已被浏览器拒绝' : '' }}</small></label>
      <button class="message-notification-open" @click="panelOpen = false; router.push('/messages')">查看消息工作台</button>
    </div>
  </div>
</template>

<style scoped>
.message-notification-control { position: relative; flex: none; }
.message-notification-button { position: relative; width: 38px; height: 38px; border: 1px solid var(--glass-border); border-radius: 50%; background: var(--glass-bg-float); color: var(--apple-text); box-shadow: var(--glass-shadow); display: grid; place-items: center; cursor: pointer; }
.message-notification-button svg { width: 18px; height: 18px; }
.message-notification-button.has-unread { color: #ff453a; animation: notification-pulse 1.4s ease-in-out infinite; }
.message-notification-count { position: absolute; top: -5px; right: -5px; min-width: 18px; height: 18px; padding: 0 4px; box-sizing: border-box; border-radius: 9px; display: grid; place-items: center; background: #ff453a; color: #fff; font-size: 10px; font-weight: 700; }
.message-notification-panel { position: absolute; top: 46px; right: 0; width: 260px; padding: 14px; border: 1px solid var(--glass-border); border-radius: 8px; background: var(--glass-bg-float); box-shadow: var(--glass-shadow-float); color: var(--apple-text); z-index: 1200; }
.message-notification-panel__header { display: flex; align-items: center; justify-content: space-between; font-size: 14px; }
.message-notification-panel__header button { border: 0; background: transparent; color: var(--apple-text2); font-size: 20px; cursor: pointer; }
.message-notification-panel p { margin: 10px 0; color: var(--apple-text2); font-size: 12px; line-height: 1.5; }
.message-notification-panel label { display: flex; align-items: center; gap: 8px; min-height: 32px; font-size: 13px; cursor: pointer; }
.message-notification-panel small { margin-left: auto; color: #ff453a; font-size: 10px; }
.message-notification-open { width: 100%; margin-top: 10px; padding: 8px 10px; border: 0; border-radius: 8px; background: #0a84ff; color: #fff; cursor: pointer; font-size: 12px; }
@keyframes notification-pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(255,69,58,.25); } 50% { box-shadow: 0 0 0 7px rgba(255,69,58,0); } }
@media (max-width: 767px) { .message-notification-panel { position: fixed; top: 62px; right: 12px; width: min(260px, calc(100vw - 24px)); box-sizing: border-box; } }
</style>
