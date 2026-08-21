import { request } from '@/utils/request'

/** 账号擦亮配置 */
export interface PolishConfig {
  xianyuAccountId: number
  /** 0:关闭 1:开启 */
  autoPolishOn: number
  /** 擦亮时刻，北京时间 HH:mm */
  polishTime: string
  /** 最近成功擦亮日期 yyyy-MM-dd */
  lastPolishDate?: string
  lastPolishAt?: number
}

/** 擦亮执行汇总 */
export interface PolishSummary {
  found: number
  success: number
  failed: number
  skipped: number
  message: string
}

/** 擦亮执行记录 */
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

// 查询账号擦亮配置
export function getPolishConfig(xianyuAccountId: number) {
  return request<PolishConfig>({
    url: '/account-tasks/polish/config',
    method: 'GET',
    params: { xianyuAccountId }
  })
}

// 保存账号擦亮配置
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
