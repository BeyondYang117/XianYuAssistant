import { request } from '@/utils/request'

/** 账号自动任务配置（擦亮 + 好评同在一行） */
export interface AccountTaskConfig {
  xianyuAccountId: number
  /** 擦亮开关 0:关闭 1:开启 */
  autoPolishOn: number
  /** 擦亮时刻，北京时间 HH:mm */
  polishTime: string
  /** 最近成功擦亮日期 yyyy-MM-dd */
  lastPolishDate?: string
  lastPolishAt?: number
  /** 好评开关 0:关闭 1:开启 */
  autoRateOn: number
  /** 好评内容 */
  rateContent: string
  /** 最近一次待评价扫描时间戳 */
  lastRateScanAt?: number
  /** 超时求评价开关 0:关闭 1:开启 */
  reviewRequestOn: number
  /** 求评价话术 */
  reviewRequestContent: string
  /** 发货后多少小时首次求评价 */
  reviewRequestDelayHours: number
  /** 再次求评价的间隔小时数 */
  reviewRequestIntervalHours: number
  /** 最多求评价次数 */
  reviewRequestMaxAttempts: number
}

/** 擦亮执行汇总 */
export interface PolishSummary {
  found: number
  success: number
  failed: number
  skipped: number
  message: string
}

/** 好评执行汇总 */
export interface RateSummary {
  found: number
  success: number
  failed: number
  skipped: number
  message: string
}

/** 求评价发送汇总 */
export interface ReviewRequestSummary {
  candidates: number
  sent: number
  failed: number
  skipped: number
  message: string
}

/** 任务执行记录 */
export interface AccountTaskRun {
  id: number
  runKey: string
  xianyuAccountId: number
  taskType: string
  targetId: string
  status: string
  successCount: number
  failedCount: number
  errorMessage: string
  startedAt: number
  finishedAt: number
}

// 查询账号自动任务配置
export function getAccountTaskConfig(xianyuAccountId: number) {
  return request<AccountTaskConfig>({
    url: '/account-tasks/config',
    method: 'GET',
    params: { xianyuAccountId }
  })
}

// 保存擦亮配置
export function savePolishConfig(data: {
  xianyuAccountId: number
  autoPolishOn: number
  polishTime: string
}) {
  return request({
    url: '/account-tasks/polish/config',
    method: 'POST',
    data
  })
}

// 立即擦亮该账号全部在售商品
export function runPolishNow(xianyuAccountId: number) {
  return request<PolishSummary>({
    url: '/account-tasks/polish/run',
    method: 'POST',
    params: { xianyuAccountId }
  })
}

// 查询擦亮执行记录
export function getPolishRuns(xianyuAccountId: number, limit = 50) {
  return request<AccountTaskRun[]>({
    url: '/account-tasks/polish/runs',
    method: 'GET',
    params: { xianyuAccountId, limit }
  })
}

// 保存好评配置
export function saveRateConfig(data: {
  xianyuAccountId: number
  autoRateOn: number
  rateContent: string
}) {
  return request({
    url: '/account-tasks/rate/config',
    method: 'POST',
    data
  })
}

// 立即评价该账号全部待评价订单
export function runRateNow(xianyuAccountId: number) {
  return request<RateSummary>({
    url: '/account-tasks/rate/run',
    method: 'POST',
    params: { xianyuAccountId }
  })
}

// 查询好评执行记录
export function getRateRuns(xianyuAccountId: number, limit = 50) {
  return request<AccountTaskRun[]>({
    url: '/account-tasks/rate/runs',
    method: 'GET',
    params: { xianyuAccountId, limit }
  })
}

// 保存超时求评价配置
export function saveReviewRequestConfig(data: {
  xianyuAccountId: number
  reviewRequestOn: number
  reviewRequestContent: string
  reviewRequestDelayHours: number
  reviewRequestIntervalHours: number
  reviewRequestMaxAttempts: number
}) {
  return request({
    url: '/account-tasks/review-request/config',
    method: 'POST',
    data
  })
}

// 立即执行超时求评价
export function runReviewRequestNow(xianyuAccountId: number) {
  return request<ReviewRequestSummary>({
    url: '/account-tasks/review-request/run',
    method: 'POST',
    params: { xianyuAccountId }
  })
}
