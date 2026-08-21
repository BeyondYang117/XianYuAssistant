import { ref, computed, nextTick } from 'vue'
import { getAccountList } from '@/api/account'
import { getConversationList } from '@/api/message'
import { getGoodsList } from '@/api/goods'
import { showInfo } from '@/utils'
import type { Account } from '@/types'
import type { ConversationSummary } from '@/api/message'
import type { GoodsItemWithConfig } from '@/api/goods'

export function useMessageManager() {
  const loading = ref(false)
  const silentLoading = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const goodsIdFilter = ref('')
  const conversationList = ref<ConversationSummary[]>([])
  const selectedConversation = ref<ConversationSummary | null>(null)
  const currentPage = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const keyword = ref('')
  const needsReplyOnly = ref(false)

  // 商品列表
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const goodsCurrentPage = ref(1)
  const goodsTotal = ref(0)
  const goodsLoading = ref(false)
  const goodsListRef = ref<HTMLElement | null>(null)

  // 计算属性
  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  const getCurrentAccountUnb = computed(() => {
    if (!selectedAccountId.value) return ''
    const account = accounts.value.find(acc => acc.id === selectedAccountId.value)
    return account ? account.unb : ''
  })


  // 加载账号列表
  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id ?? null
          await loadMessages()
          await loadGoodsList()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  // 加载消息列表
  const loadMessages = async (silent = false) => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }
    if (!silent) {
      loading.value = true
    } else {
      silentLoading.value = true
    }
    try {
      const params: any = {
        xianyuAccountId: selectedAccountId.value,
        pageNum: currentPage.value,
        pageSize: pageSize.value,
        needsReplyOnly: needsReplyOnly.value,
        keyword: keyword.value.trim()
      }
      if (goodsIdFilter.value) {
        params.xyGoodsId = goodsIdFilter.value
      }
      const response = await getConversationList(params)
      if (response.code === 0 || response.code === 200) {
        const newList = response.data?.list || []
        const newTotal = response.data?.totalCount || 0

        conversationList.value = newList
        total.value = newTotal
        if (selectedConversation.value) {
          selectedConversation.value = newList.find(item => item.sid === selectedConversation.value?.sid)
            || (silent ? selectedConversation.value : newList[0] || null)
        } else if (!silent && newList.length) {
          selectedConversation.value = newList[0] || null
        }
      } else {
        throw new Error(response.msg || '获取消息列表失败')
      }
    } catch (error: any) {
      console.error('加载消息列表失败:', error)
      if (!silent) {
        conversationList.value = []
      }
    } finally {
      loading.value = false
      silentLoading.value = false
    }
  }

  // 加载商品列表
  const loadGoodsList = async () => {
    if (!selectedAccountId.value) return
    goodsLoading.value = true
    try {
      const params: any = {
        xianyuAccountId: selectedAccountId.value,
        pageNum: goodsCurrentPage.value,
        pageSize: 20
      }
      const response = await getGoodsList(params)
      if (response.code === 0 || response.code === 200) {
        if (goodsCurrentPage.value === 1) {
          goodsList.value = response.data?.itemsWithConfig || []
        } else {
          goodsList.value.push(...(response.data?.itemsWithConfig || []))
        }
        goodsTotal.value = response.data?.totalCount || 0
        checkAndLoadMore()
      }
    } catch (error: any) {
      console.error('加载商品列表失败:', error)
      goodsList.value = []
    } finally {
      goodsLoading.value = false
    }
  }

  // 检查是否需要加载更多
  const checkAndLoadMore = () => {
    nextTick(() => {
      if (!goodsListRef.value) return
      const { scrollHeight, clientHeight } = goodsListRef.value
      if (scrollHeight <= clientHeight && goodsList.value.length < goodsTotal.value) {
        goodsCurrentPage.value++
        loadGoodsList()
      }
    })
  }

  // 商品列表滚动加载
  const handleGoodsScroll = () => {
    if (!goodsListRef.value || goodsLoading.value) return
    const { scrollTop, scrollHeight, clientHeight } = goodsListRef.value
    if (scrollTop + clientHeight >= scrollHeight - 50) {
      if (goodsList.value.length < goodsTotal.value) {
        goodsCurrentPage.value++
        loadGoodsList()
      }
    }
  }

  // 账号变更
  const handleAccountChange = () => {
    currentPage.value = 1
    goodsCurrentPage.value = 1
    goodsIdFilter.value = ''
    selectedConversation.value = null
    loadMessages()
    loadGoodsList()
  }

  // 选择商品筛选
  const selectGoods = (goodsId: string) => {
    if (goodsIdFilter.value === goodsId) {
      clearFilter()
    } else {
      goodsIdFilter.value = goodsId
      showInfo('已筛选该商品的消息')
      currentPage.value = 1
      loadMessages()
    }
  }

  // 清除筛选
  const clearFilter = () => {
    goodsIdFilter.value = ''
    showInfo('已取消筛选')
    currentPage.value = 1
    loadMessages()
  }

  const selectConversation = (conversation: ConversationSummary) => {
    selectedConversation.value = conversation
  }

  // 分页
  const handlePageChange = (page: number) => {
    currentPage.value = page
    loadMessages()
  }

  // 图片加载失败
  const handleImgError = (e: Event) => {
    const img = e.target as HTMLImageElement
    img.style.display = 'none'
  }

  return {
    loading,
    silentLoading,
    accounts,
    selectedAccountId,
    goodsIdFilter,
    conversationList,
    selectedConversation,
    currentPage,
    pageSize,
    total,
    totalPages,
    keyword,
    needsReplyOnly,
    goodsList,
    goodsCurrentPage,
    goodsTotal,
    goodsLoading,
    goodsListRef,
    getCurrentAccountUnb,
    loadAccounts,
    loadMessages,
    loadGoodsList,
    handleGoodsScroll,
    handleAccountChange,
    selectGoods,
    clearFilter,
    selectConversation,
    handlePageChange,
    handleImgError
  }
}
