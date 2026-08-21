import { ref } from 'vue'
import type { Router } from 'vue-router'
import { getAccountList } from '@/api/account'
import { getUnreadMessages, markConversationRead, type UnreadMessage } from '@/api/message'

const STORAGE_KEY = 'xianyu_message_notification_preferences'
const POLL_INTERVAL = 5000
const DEFAULT_TITLE = '闲鱼助手'

export interface MessageNotificationPreferences {
  enabled: boolean
  titleFlash: boolean
  desktop: boolean
  sound: boolean
}

const defaultPreferences: MessageNotificationPreferences = {
  enabled: true,
  titleFlash: true,
  desktop: false,
  sound: true
}

const preferences = ref<MessageNotificationPreferences>(loadPreferences())
const unreadCount = ref(0)
const unreadMessages = ref<UnreadMessage[]>([])
const polling = ref(false)
const notificationPermission = ref<NotificationPermission | 'unsupported'>(
  'Notification' in window ? Notification.permission : 'unsupported'
)
const activeConversation = ref<{ accountId: number; sid: string } | null>(null)
const knownMessageIds = new Set<number>()
let pollTimer: ReturnType<typeof setInterval> | null = null
let titleTimer: ReturnType<typeof setInterval> | null = null
let routerRef: Router | null = null
let started = false
let initialized = false

function loadPreferences() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    return { ...defaultPreferences, ...saved }
  } catch {
    return { ...defaultPreferences }
  }
}

function savePreferences(next: MessageNotificationPreferences) {
  preferences.value = { ...next }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  updateTitle()
}

async function requestDesktopPermission() {
  if (!('Notification' in window)) return 'unsupported' as const
  const permission = await Notification.requestPermission()
  notificationPermission.value = permission
  if (permission === 'granted') {
    savePreferences({ ...preferences.value, desktop: true })
  }
  return permission
}

function setActiveConversation(accountId?: number | null, sid?: string | null) {
  activeConversation.value = accountId && sid ? { accountId, sid } : null
}

async function acknowledgeConversation(accountId: number, sid: string) {
  await markConversationRead({ xianyuAccountId: accountId, sid })
  unreadMessages.value = unreadMessages.value.filter(item => !(item.accountId === accountId && item.sid === sid))
  await refreshUnread(false)
}

function messagePreview(message: UnreadMessage) {
  const content = (message.lastMessage || '').trim()
  return content || '收到一条新消息'
}

function playSound() {
  if (!preferences.value.sound) return
  try {
    const AudioContextClass = window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AudioContextClass) return
    const context = new AudioContextClass()
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.setValueAtTime(880, context.currentTime)
    gain.gain.setValueAtTime(0.0001, context.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.12, context.currentTime + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.22)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + 0.24)
    oscillator.addEventListener('ended', () => context.close())
  } catch {
    // Browsers may block audio until the page has received a user gesture.
  }
}

function showDesktopNotification(message: UnreadMessage) {
  if (!preferences.value.desktop || !('Notification' in window) || Notification.permission !== 'granted') return
  const notification = new Notification(`${message.peerUserName || '买家'} 发来新消息`, {
    body: messagePreview(message),
    icon: '/favicon.ico',
    tag: `xianyu-message-${message.accountId}-${message.sid}`
  })
  notification.onclick = () => {
    window.focus()
    routerRef?.push({ path: '/messages', query: { accountId: String(message.accountId), sid: message.sid } })
    notification.close()
  }
}

function updateTitle() {
  if (titleTimer) {
    clearInterval(titleTimer)
    titleTimer = null
  }
  if (!preferences.value.enabled || unreadCount.value <= 0) {
    document.title = DEFAULT_TITLE
    return
  }
  const unreadTitle = `(${unreadCount.value}) 买家新消息`
  document.title = `${unreadTitle} - ${DEFAULT_TITLE}`
  if (preferences.value.titleFlash && document.hidden) {
    let showAlert = false
    titleTimer = setInterval(() => {
      showAlert = !showAlert
      document.title = showAlert ? unreadTitle : DEFAULT_TITLE
    }, 900)
  }
}

async function refreshUnread(notify = true) {
  if (polling.value || !preferences.value.enabled) return
  polling.value = true
  try {
    const accountsResponse = await getAccountList()
    const accounts = accountsResponse.data?.accounts || []
    const responses = await Promise.all(accounts.map(account => getUnreadMessages(account.id).catch(() => null)))
    const allMessages = responses.flatMap(response => response?.data?.messages || [])
    const active = activeConversation.value
    const activeUnreadMessages = active
      ? allMessages.filter(message => message.accountId === active.accountId && message.sid === active.sid)
      : []
    if (activeUnreadMessages.length && active) {
      await markConversationRead({ xianyuAccountId: active.accountId, sid: active.sid })
    }
    const visibleMessages = active
      ? allMessages.filter(message => !(message.accountId === active.accountId && message.sid === active.sid))
      : allMessages
    const newMessages = initialized
      ? visibleMessages.filter(message => !knownMessageIds.has(message.lastMessageId))
      : []
    visibleMessages.forEach(message => knownMessageIds.add(message.lastMessageId))
    initialized = true
    unreadMessages.value = visibleMessages
    unreadCount.value = responses.reduce((sum, response) => sum + (response?.data?.unreadCount || 0), 0) - activeUnreadMessages.length
    unreadCount.value = Math.max(0, unreadCount.value)
    updateTitle()

    if (notify && newMessages.length) {
      const newest = [...newMessages].sort((a, b) => b.lastMessageTime - a.lastMessageTime)[0]
      if (newest) showDesktopNotification(newest)
      playSound()
    }
  } finally {
    polling.value = false
  }
}

function start(router: Router) {
  routerRef = router
  if (started) return
  started = true
  refreshUnread(true)
  pollTimer = setInterval(() => refreshUnread(true), POLL_INTERVAL)
  document.addEventListener('visibilitychange', updateTitle)
}

function stop() {
  if (pollTimer) clearInterval(pollTimer)
  if (titleTimer) clearInterval(titleTimer)
  pollTimer = null
  titleTimer = null
  started = false
  initialized = false
  knownMessageIds.clear()
  document.removeEventListener('visibilitychange', updateTitle)
  document.title = DEFAULT_TITLE
}

export function useMessageNotifications() {
  return {
    preferences,
    unreadCount,
    unreadMessages,
    polling,
    notificationPermission,
    savePreferences,
    requestDesktopPermission,
    setActiveConversation,
    acknowledgeConversation,
    refreshUnread,
    start,
    stop
  }
}
