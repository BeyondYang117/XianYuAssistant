<script setup lang="ts">
import { ref, watch } from 'vue'
import { getPolishConfig, savePolishConfig, runPolishNow, getPolishRuns } from '@/api/account-task'
import type { AccountTaskRun } from '@/api/account-task'
import { showSuccess, showError } from '@/utils'
import type { Account } from '@/types'

interface Props {
  modelValue: boolean
  account?: Account | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loading = ref(false)
const saving = ref(false)
const polishing = ref(false)
const runs = ref<AccountTaskRun[]>([])
const showRuns = ref(false)

const form = ref({
  autoPolishOn: false,
  polishTime: '03:00'
})
const lastPolishDate = ref('')

// 打开弹窗时按当前账号拉取配置；关闭时不保留上一个账号的数据
watch(() => [props.modelValue, props.account?.id], async () => {
  if (!props.modelValue || !props.account) {
    return
  }
  loading.value = true
  showRuns.value = false
  runs.value = []
  try {
    const response = await getPolishConfig(props.account.id)
    if (response.code === 0 || response.code === 200) {
      form.value.autoPolishOn = response.data?.autoPolishOn === 1
      form.value.polishTime = response.data?.polishTime || '03:00'
      lastPolishDate.value = response.data?.lastPolishDate || ''
    } else {
      throw new Error(response.msg || '读取擦亮配置失败')
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError('读取擦亮配置失败: ' + error.message)
    }
  } finally {
    loading.value = false
  }
}, { immediate: true })

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSave = async () => {
  if (!props.account) return
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(form.value.polishTime)) {
    showError('擦亮时间格式应为 HH:mm')
    return
  }

  saving.value = true
  try {
    const response = await savePolishConfig({
      xianyuAccountId: props.account.id,
      autoPolishOn: form.value.autoPolishOn ? 1 : 0,
      polishTime: form.value.polishTime
    })
    if (response.code === 0 || response.code === 200) {
      showSuccess('擦亮配置已保存')
      handleClose()
    } else {
      throw new Error(response.msg || '保存失败')
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError('保存失败: ' + error.message)
    }
  } finally {
    saving.value = false
  }
}

const handlePolishNow = async () => {
  if (!props.account) return

  polishing.value = true
  try {
    const response = await runPolishNow(props.account.id)
    if (response.code === 0 || response.code === 200) {
      const summary = response.data
      showSuccess(summary?.message || '擦亮任务已执行')
      await loadRuns()
    } else {
      throw new Error(response.msg || '擦亮失败')
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError('擦亮失败: ' + error.message)
    }
  } finally {
    polishing.value = false
  }
}

const loadRuns = async () => {
  if (!props.account) return
  try {
    const response = await getPolishRuns(props.account.id, 50)
    if (response.code === 0 || response.code === 200) {
      runs.value = response.data || []
      showRuns.value = true
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError('读取擦亮记录失败: ' + error.message)
    }
  }
}

const statusText = (status: string) => {
  switch (status) {
    case 'success': return '成功'
    case 'failed': return '失败'
    case 'running': return '执行中'
    case 'needs_review': return '待核对'
    default: return status
  }
}

const formatTs = (ts: number) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <teleport to="body">
    <div v-if="modelValue" class="modal-overlay" @click="handleClose">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h2 class="modal-title">每日擦亮</h2>
          <p class="modal-subtitle">{{ account?.accountNote || account?.unb || '' }}</p>
        </div>

        <div class="modal-body">
          <div class="field field--row">
            <span class="field__label">开启每日自动擦亮</span>
            <button
              class="toggle"
              :class="{ 'toggle--on': form.autoPolishOn }"
              :disabled="loading"
              @click="form.autoPolishOn = !form.autoPolishOn"
            >
              <span class="toggle__track"><span class="toggle__thumb"></span></span>
            </button>
          </div>

          <div class="field">
            <label class="field__label" for="polish-time">擦亮时刻（北京时间）</label>
            <input
              id="polish-time"
              v-model="form.polishTime"
              type="time"
              class="field__input"
              :disabled="loading"
            />
            <p class="field__hint">每天到点后擦亮该账号全部在售商品，一天只执行一轮</p>
          </div>

          <div class="field" v-if="lastPolishDate">
            <span class="field__label">最近擦亮日期</span>
            <span class="field__value">{{ lastPolishDate }}</span>
          </div>

          <div class="field field--actions">
            <button class="link-btn" :disabled="polishing" @click="handlePolishNow">
              {{ polishing ? '擦亮中…' : '立即擦亮' }}
            </button>
            <button class="link-btn" @click="loadRuns">查看执行记录</button>
          </div>

          <div class="runs" v-if="showRuns">
            <p class="runs__empty" v-if="runs.length === 0">暂无执行记录</p>
            <ul class="runs__list" v-else>
              <li v-for="run in runs" :key="run.id" class="runs__item">
                <span class="runs__target">{{ run.targetId }}</span>
                <span class="runs__status" :class="`runs__status--${run.status}`">
                  {{ statusText(run.status) }}
                </span>
                <span class="runs__time">{{ formatTs(run.startedAt) }}</span>
                <span class="runs__error" v-if="run.errorMessage && run.status !== 'success'">
                  {{ run.errorMessage }}
                </span>
              </li>
            </ul>
          </div>
        </div>

        <div class="modal-footer">
          <button class="modal-btn modal-btn-cancel" @click="handleClose">取消</button>
          <div class="modal-divider"></div>
          <button class="modal-btn modal-btn-primary" :disabled="saving || loading" @click="handleSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}

.modal {
  width: 380px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  animation: scaleIn 0.2s ease;
}

.modal-header {
  padding: 16px;
  text-align: center;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.1);
}

.modal-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #000;
  line-height: 1.2;
}

.modal-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(28, 28, 30, 0.55);
}

.modal-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field--row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.field--actions {
  flex-direction: row;
  gap: 16px;
}

.field__label {
  font-size: 14px;
  color: #1c1c1e;
  font-weight: 500;
}

.field__value {
  font-size: 14px;
  color: rgba(28, 28, 30, 0.55);
}

.field__hint {
  margin: 0;
  font-size: 12px;
  color: rgba(28, 28, 30, 0.5);
  line-height: 1.4;
}

.field__input {
  height: 42px;
  border-radius: 12px;
  border: none;
  padding: 0 12px;
  font-size: 15px;
  background: rgba(0, 0, 0, 0.05);
  color: #000;
  outline: none;
  box-sizing: border-box;
}

.field__input:focus {
  background: rgba(0, 0, 0, 0.08);
}

.toggle {
  display: inline-flex;
  align-items: center;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  -webkit-tap-highlight-color: transparent;
}

.toggle:disabled {
  opacity: 0.5;
  cursor: default;
}

.toggle__track {
  width: 44px;
  height: 26px;
  border-radius: 13px;
  background: rgba(0, 0, 0, 0.12);
  position: relative;
  transition: background 0.2s ease;
  display: block;
}

.toggle--on .toggle__track {
  background: #30d158;
}

.toggle__thumb {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  position: absolute;
  top: 2px;
  left: 2px;
  transition: transform 0.2s ease;
}

.toggle--on .toggle__thumb {
  transform: translateX(18px);
}

.link-btn {
  background: none;
  border: none;
  padding: 0;
  font-size: 14px;
  color: #0a84ff;
  cursor: pointer;
}

.link-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.runs {
  border-top: 0.5px solid rgba(0, 0, 0, 0.1);
  padding-top: 12px;
}

.runs__empty {
  margin: 0;
  font-size: 13px;
  color: rgba(28, 28, 30, 0.5);
}

.runs__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.runs__item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 8px;
  font-size: 12px;
  padding-bottom: 8px;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

.runs__target {
  color: #1c1c1e;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runs__status--success {
  color: #30d158;
}

.runs__status--failed,
.runs__status--needs_review {
  color: #ff453a;
}

.runs__time,
.runs__error {
  grid-column: 1 / -1;
  color: rgba(28, 28, 30, 0.5);
  word-break: break-all;
}

.modal-footer {
  display: flex;
  height: 48px;
  border-top: 0.5px solid rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}

.modal-btn {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  -webkit-tap-highlight-color: transparent;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-btn:active {
  opacity: 0.5;
}

.modal-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.modal-btn-cancel {
  color: #666;
}

.modal-btn-primary {
  color: #007aff;
}

.modal-divider {
  width: 0.5px;
  background: rgba(0, 0, 0, 0.1);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes scaleIn {
  from { transform: scale(0.9); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
