<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ConversationSummary } from '@/api/message'
import { useMessageManager } from './useMessageManager'
import './messages.css'
import IconMessage from '@/components/icons/IconMessage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import GoodsSidebar from './components/GoodsSidebar.vue'
import ConversationList from './components/ConversationList.vue'
import ChatPanel from './components/ChatPanel.vue'
import { useMessageNotifications } from '@/composables/useMessageNotifications'

const {
  loading, silentLoading, accounts, selectedAccountId, goodsIdFilter,
  conversationList, selectedConversation, currentPage, total, totalPages,
  keyword, needsReplyOnly, goodsList, goodsTotal, goodsLoading,
  getCurrentAccountUnb, loadAccounts, loadMessages, handleAccountChange,
  selectGoods, clearFilter, selectConversation, handlePageChange
} = useMessageManager()
const route = useRoute()
const { setActiveConversation, acknowledgeConversation } = useMessageNotifications()

const sidebarCollapsed = ref(false)
const mobileView = ref<'conversations' | 'chat'>('conversations')
const searchInput = ref(keyword.value)
let refreshTimer: ReturnType<typeof setInterval> | null = null
const totalPagesSafe = computed(() => Math.max(1, totalPages.value))
const selectedGoodsName = computed(() => {
  if (!selectedConversation.value?.xyGoodsId) return ''
  return goodsList.value.find(item => item.item.xyGoodId === selectedConversation.value?.xyGoodsId)?.item.title || ''
})

const applySearch = () => {
  keyword.value = searchInput.value.trim()
  currentPage.value = 1
  loadMessages()
}
const toggleNeedsReply = () => {
  needsReplyOnly.value = !needsReplyOnly.value
  currentPage.value = 1
  loadMessages()
}
const selectConversationAndOpen = (conversation: ConversationSummary) => {
  selectConversation(conversation)
  mobileView.value = 'chat'
}
const pageButtons = computed(() => {
  const end = Math.min(totalPagesSafe.value, Math.max(5, currentPage.value + 2))
  const start = Math.max(1, end - 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

const applyNotificationTarget = async () => {
  const queryAccountId = Number(route.query.accountId)
  if (queryAccountId && accounts.value.some(account => account.id === queryAccountId) && selectedAccountId.value !== queryAccountId) {
    selectedAccountId.value = queryAccountId
    await handleAccountChange()
  }
  const querySid = typeof route.query.sid === 'string' ? route.query.sid : ''
  if (querySid) {
    const conversation = conversationList.value.find(item => item.sid === querySid)
    if (conversation) selectConversationAndOpen(conversation)
  }
}

onMounted(async () => {
  await loadAccounts()
  await applyNotificationTarget()
  refreshTimer = setInterval(() => {
    if (selectedAccountId.value) loadMessages(true)
  }, 5000)
})
onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  setActiveConversation(null, null)
})

watch([selectedAccountId, () => selectedConversation.value?.sid, mobileView], ([accountId, sid, view]) => {
  const conversationVisible = window.innerWidth >= 768 || view === 'chat'
  if (accountId && sid && conversationVisible) {
    setActiveConversation(accountId, sid)
    acknowledgeConversation(accountId, sid).catch(() => undefined)
  } else {
    setActiveConversation(null, null)
  }
})

watch(() => [route.query.accountId, route.query.sid], () => {
  applyNotificationTarget()
})
</script>

<template>
  <div class="messages-workbench">
    <header class="workbench-toolbar">
      <div class="workbench-toolbar__title">
        <span class="workbench-toolbar__icon"><IconMessage /></span>
        <div><h1>消息工作台</h1><p>{{ total }} 个会话<span v-if="silentLoading"> · 正在同步</span></p></div>
      </div>
      <div class="workbench-toolbar__actions">
        <div class="workbench-account">
          <select v-model="selectedAccountId" @change="handleAccountChange">
            <option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountNote || account.unb }}</option>
          </select>
          <IconChevronDown />
        </div>
        <button class="workbench-icon-button" :class="{ loading }" :disabled="loading" title="刷新" @click="loadMessages()"><IconRefresh /></button>
      </div>
    </header>

    <div class="workbench-filterbar">
      <form class="workbench-search" @submit.prevent="applySearch">
        <IconSearch />
        <input v-model="searchInput" placeholder="搜索用户、消息或商品 ID" />
        <button v-if="searchInput" type="button" @click="searchInput = ''; applySearch()">清除</button>
      </form>
      <button class="workbench-filter" :class="{ active: needsReplyOnly }" @click="toggleNeedsReply">待回复 <span v-if="needsReplyOnly">已筛选</span></button>
      <button v-if="goodsIdFilter" class="workbench-filter active" @click="clearFilter">商品：{{ goodsIdFilter }} ×</button>
      <span class="workbench-filterbar__hint">{{ currentPage }} / {{ totalPagesSafe }}</span>
    </div>

    <main class="workbench-body">
      <aside class="workbench-goods" :class="{ collapsed: sidebarCollapsed }">
        <div v-if="!sidebarCollapsed" class="workbench-goods__inner">
          <GoodsSidebar :goods-list="goodsList" :goods-total="goodsTotal" :goods-loading="goodsLoading" :goods-id-filter="goodsIdFilter" @select="selectGoods" @clear-filter="clearFilter" />
        </div>
        <button class="workbench-goods__toggle" :title="sidebarCollapsed ? '展开商品筛选' : '收起商品筛选'" @click="sidebarCollapsed = !sidebarCollapsed">{{ sidebarCollapsed ? '商品' : '‹' }}</button>
      </aside>

      <section class="workbench-conversations" :class="{ 'mobile-hidden': mobileView === 'chat' }">
        <div class="workbench-section-title"><strong>会话</strong><span>{{ conversationList.length }}</span></div>
        <ConversationList :conversations="conversationList" :selected-sid="selectedConversation?.sid" :loading="loading" @select="selectConversationAndOpen" />
        <div v-if="totalPages > 1" class="workbench-pagination">
          <button :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)">‹</button>
          <button v-for="page in pageButtons" :key="page" :class="{ active: page === currentPage }" @click="handlePageChange(page)">{{ page }}</button>
          <button :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)">›</button>
        </div>
      </section>

      <section class="workbench-chat" :class="{ 'mobile-visible': mobileView === 'chat' }">
        <button class="workbench-mobile-back" @click="mobileView = 'conversations'">‹ 返回会话</button>
        <ChatPanel :conversation="selectedConversation" :account-id="selectedAccountId || undefined" :account-unb="getCurrentAccountUnb" :goods-name="selectedGoodsName" />
      </section>
    </main>
  </div>
</template>
