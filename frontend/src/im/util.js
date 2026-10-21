/**
 * IM通用工具
 */

/**
 * 时间格式化：今天 HH:MM，昨天"昨天"，更早 MM-DD
 */
export function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return String(time)
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const sameDay = (a, b) => a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  if (sameDay(d, now)) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(d, yesterday)) {
    return '昨天'
  }
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 消息完整时间（气泡hover/分栏）
 */
export function formatFullTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return String(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 按ID稳定取头像底色
 */
const COLORS = ['#667eea', '#f56c6c', '#e6a23c', '#67c23a', '#409eff', '#9a6fe0', '#f093fb', '#38f9d7']
export function avatarColor(id) {
  const n = typeof id === 'number' ? id : Math.abs(String(id || '').split('').reduce((s, c) => s + c.charCodeAt(0), 0))
  return COLORS[n % COLORS.length]
}
