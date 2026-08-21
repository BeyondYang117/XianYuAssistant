<script setup lang="ts">
import type { ConversationSummary } from '@/api/message'
import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconMessage from '@/components/icons/IconMessage.vue'

defineProps<{
  conversations: ConversationSummary[]
  selectedSid?: string
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'select', conversation: ConversationSummary): void }>()

const formatTime = (value: string | number) => {
  const date = new Date(Number(value))
  if (Number.isNaN(date.getTime())) return '-'
  const diff = Date.now() - date.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}小时前`
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const preview = (conversation: ConversationSummary) => {
  const text = conversation.lastMessage || ''
  if (conversation.lastContentType === 2) return text || '[图片]'
  return text.replace(/^\[图片\]\s*/, '[图片]') || '暂无文字消息'
}
</script>

<template>
  <div class="conversation-list">
    <div v-if="loading" class="conversation-list__state">加载会话中...</div>
    <div v-else-if="!conversations.length" class="conversation-list__state">
      <IconEmpty />
      <span>暂无匹配会话</span>
    </div>
    <button
      v-for="conversation in conversations"
      :key="conversation.sid"
      class="conversation-item"
      :class="{ 'conversation-item--active': conversation.sid === selectedSid }"
      @click="emit('select', conversation)"
    >
      <span class="conversation-item__avatar"><IconMessage /></span>
      <span class="conversation-item__main">
        <span class="conversation-item__topline">
          <strong>{{ conversation.peerUserName || conversation.peerUserId || '用户' }}</strong>
          <time>{{ formatTime(conversation.lastMessageTime) }}</time>
        </span>
        <span class="conversation-item__preview">{{ preview(conversation) }}</span>
        <span class="conversation-item__meta">
          <span v-if="conversation.xyGoodsId">商品 {{ conversation.xyGoodsId }}</span>
          <span>{{ conversation.messageCount }} 条消息</span>
        </span>
      </span>
      <span v-if="conversation.needsReply" class="conversation-item__badge">待回复</span>
    </button>
  </div>
</template>

<style scoped>
.conversation-list { height: 100%; overflow-y: auto; padding: 6px; }
.conversation-list__state { min-height: 220px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:10px; color:var(--msg-muted); font-size:13px; }
.conversation-list__state svg { width:32px; height:32px; opacity:.35; }
.conversation-item { width:100%; display:flex; align-items:flex-start; gap:10px; padding:12px 10px; border:0; border-radius:12px; background:transparent; text-align:left; color:inherit; cursor:pointer; transition:.16s ease; }
.conversation-item:hover { background:rgba(0,122,255,.06); }
.conversation-item--active { background:rgba(0,122,255,.11); box-shadow:inset 3px 0 #0a84ff; }
.conversation-item__avatar { width:34px; height:34px; flex:none; display:grid; place-items:center; border-radius:50%; background:rgba(10,132,255,.12); color:#0a84ff; }
.conversation-item__avatar svg { width:17px; height:17px; }
.conversation-item__main { min-width:0; flex:1; display:flex; flex-direction:column; gap:4px; }
.conversation-item__topline { display:flex; align-items:center; gap:8px; }
.conversation-item__topline strong { min-width:0; flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:13px; }
.conversation-item__topline time { flex:none; font-size:11px; color:var(--msg-muted); }
.conversation-item__preview { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:var(--msg-secondary); font-size:12px; }
.conversation-item__meta { display:flex; gap:8px; overflow:hidden; color:var(--msg-muted); font-size:10px; }
.conversation-item__meta span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.conversation-item__badge { flex:none; padding:3px 6px; border-radius:8px; color:#fff; background:#ff453a; font-size:10px; }
</style>
