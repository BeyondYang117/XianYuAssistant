<script setup lang="ts">
import { ref } from 'vue'
const copied = ref(false)
const platform = ref<'bash' | 'powershell'>('bash')
const image = 'abu116/xianyu-help:latest'
const commands = {
  bash: `chmod +x deploy.sh && ./deploy.sh`,
  powershell: `$dataDir = Join-Path $PWD "data"
New-Item -ItemType Directory -Force (Join-Path $dataDir "dbdata"), (Join-Path $dataDir "logs") | Out-Null
$secretFile = Join-Path $dataDir ".jwt_secret"
if (!(Test-Path $secretFile)) {
  $bytes = New-Object byte[] 48
  [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
  [Convert]::ToBase64String($bytes) | Set-Content -NoNewline $secretFile
}
$jwtSecret = Get-Content -Raw $secretFile
docker pull ${image}
docker rm -f xianyu-assistant 2>$null
docker run -d --name xianyu-assistant -p 12400:12400 -e "SERVER_PORT=12400" -e "JAVA_OPTS=-Xms256m -Xmx1024m" -e "TZ=Asia/Shanghai" -e "JWT_SECRET=$jwtSecret" -v "\${dataDir}\\dbdata:/app/dbdata" -v "\${dataDir}\\logs:/app/logs" --restart unless-stopped ${image}`
}
const command = () => commands[platform.value]
const copyCommand = async () => { try { await navigator.clipboard.writeText(command()); copied.value = true; window.setTimeout(() => { copied.value = false }, 1800) } catch { copied.value = false } }
</script>
<template>
  <main class="deploy-page">
    <header class="deploy-header"><div><div class="deploy-kicker">XIANYU ASSISTANT</div><h1>部署指南</h1><p>用 Docker 在几分钟内启动你的闲鱼自动化工作台。</p></div><a class="deploy-back" href="/login">进入管理后台</a></header>
    <section class="deploy-grid">
      <article class="deploy-card deploy-card--primary"><div class="deploy-card-head"><div><span class="deploy-step">01</span><h2>一键部署</h2></div><span class="deploy-status">推荐</span></div><p class="deploy-muted">脚本会自动生成并持久化 JWT_SECRET，升级时复用同一个密钥。Linux/macOS 使用 Bash，Windows 使用 PowerShell。</p><div class="deploy-tabs"><button type="button" :class="{ active: platform === 'bash' }" @click="platform = 'bash'">Linux / macOS</button><button type="button" :class="{ active: platform === 'powershell' }" @click="platform = 'powershell'">Windows PowerShell</button></div><div class="deploy-code-wrap"><div class="deploy-code-label">{{ platform === 'bash' ? 'Bash' : 'PowerShell' }}</div><button class="deploy-copy" type="button" @click="copyCommand">{{ copied ? '已复制' : '复制命令' }}</button><pre><code>{{ command() }}</code></pre></div><a class="deploy-script-link" href="/deploy.sh" download>下载 deploy.sh 一键脚本（Bash）</a><p class="deploy-footnote">密钥保存在 <code>data/.jwt_secret</code>。不要删除或提交该文件；可通过 <code>PORT</code>、<code>DATA_DIR</code>、<code>LOG_DIR</code>、<code>JAVA_OPTS</code>、<code>TZ</code> 自定义部署。</p></article>
      <article class="deploy-card"><span class="deploy-step">02</span><h2>打开服务</h2><p class="deploy-muted">容器启动后，在浏览器访问：</p><code class="deploy-url">http://localhost:12400</code><div class="deploy-note">首次使用请先注册管理员账号。数据保存在当前目录的 <code>data/dbdata</code>，升级时不要更改路径。</div></article>
      <article class="deploy-card"><span class="deploy-step">03</span><h2>更新版本</h2><p class="deploy-muted">在部署目录重新执行脚本即可完成更新，数据目录会被保留：</p><pre class="deploy-inline-code"><code>bash deploy.sh</code></pre><p class="deploy-footnote">脚本会拉取最新版镜像、替换容器并复用 <code>data</code> 目录。</p></article>
    </section>
    <footer class="deploy-footer"><span>镜像：{{ image }}</span><a href="https://github.com/BeyondYang117/XianYuAssistant" target="_blank" rel="noreferrer">查看源代码</a></footer>
  </main>
</template>
<style scoped>
.deploy-page{min-height:100vh;padding:56px clamp(22px,7vw,112px) 42px;color:#1d2433;background:linear-gradient(135deg,#eef5ff 0%,#f9f3ff 52%,#fff8f1 100%)}.deploy-header{max-width:1120px;margin:0 auto 42px;display:flex;align-items:flex-end;justify-content:space-between;gap:24px}.deploy-kicker{color:#5665c6;font-size:12px;font-weight:700;letter-spacing:1.6px}h1{margin:10px 0 8px;font-size:clamp(32px,5vw,54px);line-height:1.05}.deploy-header p{margin:0;color:#657087;font-size:16px}.deploy-back,.deploy-script-link{color:#4c5ac4;text-decoration:none;font-weight:600}.deploy-back{border:1px solid #cbd2f4;background:#fff9;padding:11px 16px;border-radius:8px;white-space:nowrap}.deploy-grid{max-width:1120px;margin:0 auto;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.deploy-card{padding:26px;border:1px solid #fffc;border-radius:12px;background:#fffb;box-shadow:0 16px 45px #4046781a;backdrop-filter:blur(18px)}.deploy-card--primary{grid-column:1/-1}.deploy-card-head{display:flex;justify-content:space-between;align-items:flex-start}.deploy-step{color:#6873ca;font-size:12px;font-weight:700;letter-spacing:1px}h2{margin:8px 0 12px;font-size:23px}.deploy-muted{color:#687287;line-height:1.65}.deploy-status{color:#16825f;background:#e6f7ee;padding:5px 9px;border-radius:6px;font-size:12px;font-weight:700}.deploy-code-wrap{position:relative;margin:22px 0 16px}.deploy-code-label{position:absolute;top:10px;left:14px;color:#8e9bc2;font:11px ui-monospace,monospace;letter-spacing:.5px}.deploy-code-wrap pre,.deploy-inline-code{padding:34px 20px 18px;border-radius:8px;background:#202638;color:#e6edff;font:13px/1.7 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}.deploy-copy{position:absolute;top:8px;right:10px;border:0;border-radius:6px;padding:7px 10px;color:#dfe6ff;background:#3b4667;cursor:pointer}.deploy-url{display:block;margin:18px 0;padding:14px 16px;border-radius:8px;background:#f0f2fa;color:#38458f;font:600 15px ui-monospace,monospace}.deploy-note{padding:13px 14px;border-left:3px solid #57a9e7;background:#edf7ff;color:#637187;line-height:1.6;font-size:13px}.deploy-footnote{margin:14px 0 0;color:#7c8495;font-size:13px}.deploy-footer{max-width:1120px;margin:28px auto 0;display:flex;justify-content:space-between;gap:16px;color:#7c8495;font-size:13px}.deploy-footer a{color:#5665c6;text-decoration:none}@media(max-width:760px){.deploy-page{padding:34px 18px 28px}.deploy-header{display:block}.deploy-back{display:inline-block;margin-top:20px}.deploy-grid{grid-template-columns:1fr}.deploy-card--primary{grid-column:auto}.deploy-footer{display:block}.deploy-footer a{display:block;margin-top:8px}}
.deploy-tabs{display:flex;gap:8px;margin:20px 0 0}.deploy-tabs button{border:1px solid #cbd2f4;border-radius:6px;padding:8px 12px;background:#fff;color:#4c5ac4;font-weight:600;cursor:pointer}.deploy-tabs button.active{border-color:#5665c6;background:#5665c6;color:#fff}@media(max-width:480px){.deploy-tabs{flex-direction:column}.deploy-tabs button{width:100%;text-align:left}}
</style>
