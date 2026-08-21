<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getContextMessages, sendMessage as sendMessageApi, type ChatMessage, type ConversationSummary } from '@/api/message'
import { sendImageMessage as sendImageMessageApi } from '@/api/image'
import { toast } from '@/utils/toast'
import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconImage from '@/components/icons/IconImage.vue'
import IconSend from '@/components/icons/IconSend.vue'
import MultiImageUploader from '@/components/MultiImageUploader.vue'

const props = defineProps<{
  conversation: ConversationSummary | null
  accountId?: number
  accountUnb: string
  goodsName?: string
}>()

const messages = ref<ChatMessage[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const hasMore = ref(true)
const inputText = ref('')
const imageUrls = ref('')
const showUploader = ref(false)
const previewUrl = ref('')
const listRef = ref<HTMLElement | null>(null)
const previewRef = ref<HTMLElement | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

const peerName = computed(() => props.conversation?.peerUserName || props.conversation?.peerUserId || '选择一个会话')
const isUser = (message: ChatMessage) => (message.contentType === 1 || message.contentType === 2) && message.senderUserId !== props.accountUnb
const isMine = (message: ChatMessage) => !isUser(message) && [1, 2, 999, 997, 888, 887].includes(message.contentType)
const replyLabel = (message: ChatMessage) => {
  if (message.contentType === 999 || message.contentType === 997) return '人工回复'
  if (message.contentType === 888 || message.contentType === 887) return 'AI自动回复'
  return ''
}
const images = (message: ChatMessage) => message.imageUrls?.length ? message.imageUrls : (message.msgContent?.startsWith('[图片]') && /https?:/.test(message.msgContent) ? [message.msgContent.slice(4).trim()] : [])
const text = (message: ChatMessage) => images(message).length && (message.msgContent || '').startsWith('[图片]') ? '' : (message.msgContent || '')
const time = (value: string | number) => new Date(Number(value)).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
const openPreview = (url: string) => { previewUrl.value = url }
const closePreview = () => { previewUrl.value = '' }
const handlePreviewKeydown = (event: KeyboardEvent) => { if (event.key === 'Escape') closePreview() }

const scrollBottom = () => nextTick(() => { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight })
const fetchMessages = async (append = false, silent = false) => {
  if (!props.conversation?.sid) return
  if (append) loadingMore.value = true
  else if (!silent) { loading.value = true; messages.value = []; hasMore.value = true }
  try {
    const offset = append ? messages.value.length : 0
    const response = await getContextMessages({ sid: props.conversation.sid, limit: 30, offset })
    const list = Array.isArray(response.data) ? response.data : []
    if (append) messages.value = [...list.reverse(), ...messages.value]
    else if (silent) {
      const known = new Set(messages.value.map(item => item.id))
      const incoming = list.reverse().filter(item => !known.has(item.id))
      if (incoming.length) messages.value = [...messages.value, ...incoming]
    } else messages.value = list.reverse()
    hasMore.value = list.length >= 30
    if (!append && !silent) scrollBottom()
    if (silent && list.length && list[list.length - 1]?.id !== messages.value[messages.value.length - 1]?.id) scrollBottom()
  } catch (error) { console.error('加载会话消息失败', error) }
  finally { loading.value = false; loadingMore.value = false }
}
const refresh = () => fetchMessages(false, true)
const handleScroll = () => { if (listRef.value?.scrollTop !== undefined && listRef.value.scrollTop < 48 && hasMore.value && !loadingMore.value) fetchMessages(true) }
const send = async () => {
  if (!props.conversation?.sid || !props.accountId || !props.conversation.peerUserId || sending.value) return
  const urls = imageUrls.value.split(',').map(item => item.trim()).filter(Boolean)
  if (!inputText.value.trim() && !urls.length) return toast.warning('请输入消息内容或上传图片')
  sending.value = true
  try {
    const cid = props.conversation.sid.replace('@goofish', '')
    const toId = props.conversation.peerUserId.replace('@goofish', '')
    for (const url of urls) await sendImageMessageApi({ xianyuAccountId: props.accountId, cid, toId, imageUrl: url, width: 800, height: 800, xyGoodsId: props.conversation.xyGoodsId })
    if (inputText.value.trim()) await sendMessageApi({ xianyuAccountId: props.accountId, cid, toId, text: inputText.value.trim(), xyGoodsId: props.conversation.xyGoodsId })
    inputText.value = ''; imageUrls.value = ''; showUploader.value = false
    toast.success('发送成功'); await fetchMessages()
  } catch (error: any) { toast.error(error?.message || '发送失败') }
  finally { sending.value = false }
}
const keydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  send()
}
watch(() => props.conversation?.sid, () => { if (timer) clearInterval(timer); messages.value = []; if (props.conversation) { fetchMessages(); timer = setInterval(refresh, 3000) } }, { immediate: true })
watch(previewUrl, value => { if (value) nextTick(() => previewRef.value?.focus()) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <section class="chat-panel">
    <header class="chat-panel__header">
      <div><h2>{{ peerName }}</h2><span v-if="conversation?.xyGoodsId">商品 {{ conversation.xyGoodsId }}</span></div>
      <button v-if="conversation" class="chat-panel__refresh" @click="fetchMessages()">刷新</button>
    </header>
    <div v-if="!conversation" class="chat-panel__empty"><IconEmpty /><span>从左侧选择一个会话开始处理</span></div>
    <div v-else ref="listRef" class="chat-panel__messages" @scroll="handleScroll">
      <div v-if="loadingMore" class="chat-panel__loading-more">加载更早的消息...</div>
      <div v-if="loading" class="chat-panel__loading">加载会话中...</div>
      <template v-else-if="messages.length">
        <div v-for="message in messages" :key="message.id" class="chat-message" :class="{ 'chat-message--mine': isMine(message), 'chat-message--system': !isUser(message) && !isMine(message) }">
          <div v-if="!isUser(message) && !isMine(message)" class="chat-message__system">{{ message.msgContent }}</div>
          <template v-else>
            <div class="chat-message__meta">
              <span>{{ isUser(message) ? (message.senderUserName || peerName) : '我' }}</span>
              <span
                v-if="replyLabel(message)"
                class="chat-message__reply-label"
                :class="{ 'chat-message__reply-label--ai': message.contentType === 888 || message.contentType === 887 }"
              >{{ replyLabel(message) }}</span>
              <span>{{ time(message.messageTime) }}</span>
            </div>
            <div v-if="images(message).length" class="chat-message__images">
              <button v-for="url in images(message)" :key="url" class="chat-message__image-button" type="button" title="点击查看大图" @click="openPreview(url)"><img :src="url" alt="聊天图片" /></button>
            </div>
            <div v-if="text(message)" class="chat-message__bubble">{{ text(message) }}</div>
          </template>
        </div>
      </template>
      <div v-else class="chat-panel__empty"><IconEmpty /><span>暂无消息</span></div>
    </div>
    <footer v-if="conversation" class="chat-panel__composer">
      <div v-if="showUploader" class="chat-panel__uploader"><MultiImageUploader v-if="accountId" :account-id="accountId" v-model="imageUrls" /></div>
      <textarea v-model="inputText" placeholder="输入消息，Enter 发送，Shift+Enter 换行" @keydown="keydown" />
      <div class="chat-panel__composer-actions"><button :class="{ active: showUploader }" @click="showUploader = !showUploader"><IconImage /></button><button class="send" :disabled="sending" @click="send"><IconSend /></button></div>
    </footer>
  </section>
  <Teleport to="body">
    <Transition name="image-preview">
      <div v-if="previewUrl" ref="previewRef" class="image-preview-overlay" tabindex="0" @click.self="closePreview" @keydown="handlePreviewKeydown">
        <button class="image-preview-overlay__close" type="button" aria-label="关闭图片预览" @click="closePreview">×</button>
        <img class="image-preview-overlay__image" :src="previewUrl" alt="图片预览" @click.stop />
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.chat-panel { min-width:0; min-height:0; height:100%; display:flex; flex-direction:column; }
.chat-panel__header { display:flex; align-items:center; justify-content:space-between; padding:16px 20px; border-bottom:1px solid var(--msg-border); flex:none; }
.chat-panel__header h2 { margin:0 0 3px; font-size:16px; }.chat-panel__header span { color:var(--msg-muted); font-size:11px; }.chat-panel__refresh { border:0; background:transparent; color:#0a84ff; cursor:pointer; }
.chat-panel__messages { flex:1; min-height:0; overflow:auto; padding:20px 20px 28px; scroll-padding-bottom:28px; background:rgba(248,249,251,.55); }.chat-panel__empty,.chat-panel__loading { height:100%; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:10px; color:var(--msg-muted); font-size:13px; }.chat-panel__empty svg { width:38px; height:38px; opacity:.35; }.chat-panel__loading-more { text-align:center; color:var(--msg-muted); font-size:11px; margin-bottom:12px; }
.chat-message { display:flex; flex-direction:column; align-items:flex-start; margin-bottom:16px; }.chat-message--mine { align-items:flex-end; }.chat-message__meta { display:flex; align-items:center; gap:5px; margin-bottom:4px; color:var(--msg-muted); font-size:11px; }.chat-message__reply-label { padding:1px 6px; border-radius:8px; color:#0a6fca; background:rgba(10,132,255,.11); font-size:10px; font-weight:600; line-height:1.5; }.chat-message__reply-label--ai { color:#9b3fc2; background:rgba(191,90,242,.12); }.chat-message__bubble { max-width:min(72%, 520px); padding:10px 13px; border-radius:14px 14px 14px 4px; background:#fff; line-height:1.5; font-size:13px; white-space:pre-wrap; word-break:break-word; box-shadow:0 1px 3px rgba(0,0,0,.05); }.chat-message--mine .chat-message__bubble { border-radius:14px 14px 4px 14px; background:#d9f7df; }.chat-message__system { padding:5px 10px; border-radius:10px; background:rgba(0,0,0,.05); color:var(--msg-muted); font-size:11px; }.chat-message__images { display:flex; gap:6px; flex-wrap:wrap; }.chat-message__image-button { padding:0; border:0; border-radius:9px; overflow:hidden; background:rgba(0,0,0,.04); cursor:zoom-in; }.chat-message__images img { display:block; max-width:180px; max-height:180px; border-radius:9px; object-fit:cover; transition:transform .16s ease; }.chat-message__image-button:hover img { transform:scale(1.03); }
.chat-panel__composer { position:relative; z-index:2; padding:12px 16px; border-top:1px solid var(--msg-border); background:#f7f7f9; box-shadow:0 -8px 20px rgba(0,0,0,.04); }.chat-panel__composer textarea { width:100%; min-height:54px; box-sizing:border-box; resize:none; border:1px solid var(--msg-border); border-radius:10px; padding:10px 12px; font:inherit; background:#fff; }.chat-panel__composer textarea:focus { outline:0; border-color:#0a84ff; }.chat-panel__composer-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:7px; }.chat-panel__composer-actions button { width:34px; height:30px; border:0; border-radius:8px; display:grid; place-items:center; color:var(--msg-secondary); cursor:pointer; }.chat-panel__composer-actions button.active { color:#0a84ff; background:rgba(10,132,255,.1); }.chat-panel__composer-actions svg { width:17px; height:17px; }.chat-panel__composer-actions .send { color:#fff; background:#0a84ff; }.chat-panel__composer-actions .send:disabled { opacity:.5; }.chat-panel__uploader { margin-bottom:8px; }
@media (max-width: 760px) { .chat-panel__header { padding:12px 14px; }.chat-panel__messages { padding:14px; }.chat-message__bubble { max-width:82%; } }
.image-preview-overlay { position:fixed; inset:0; z-index:3000; display:flex; align-items:center; justify-content:center; padding:32px; background:rgba(0,0,0,.72); backdrop-filter:blur(8px); cursor:zoom-out; }.image-preview-overlay__image { display:block; max-width:calc(100vw - 64px); max-height:calc(100vh - 64px); object-fit:contain; border-radius:8px; box-shadow:0 12px 48px rgba(0,0,0,.35); cursor:default; }.image-preview-overlay__close { position:absolute; top:18px; right:22px; width:36px; height:36px; border:0; border-radius:50%; color:#fff; background:rgba(255,255,255,.16); font-size:28px; line-height:1; cursor:pointer; }.image-preview-enter-active,.image-preview-leave-active { transition:opacity .18s ease; }.image-preview-enter-from,.image-preview-leave-to { opacity:0; }
</style>
