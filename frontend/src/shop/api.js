import axios from 'axios'
import { ElMessage } from 'element-plus'
import { shopStore } from './store'

/**
 * 商店API封装：自动带token、统一错误提示
 */
const api = axios.create({ baseURL: '/api/shop' })

api.interceptors.request.use(config => {
  if (shopStore.token) {
    config.headers.Authorization = 'Bearer ' + shopStore.token
  }
  return config
})

api.interceptors.response.use(
  response => {
    const body = response.data
    if (body && body.code !== undefined && body.code !== 200) {
      if (body.code === 401) {
        shopStore.clearAuth()
        shopStore.loginVisible = true
      }
      ElMessage.error(body.msg || '操作失败')
      return Promise.reject(new Error(body.msg))
    }
    return body && body.data !== undefined ? body.data : body
  },
  error => {
    let msg = error.message || '网络错误'
    if (error.response) {
      const body = error.response.data
      msg = (body && body.msg) || `请求失败(${error.response.status})`
      if (error.response.status === 401) {
        shopStore.clearAuth()
        shopStore.loginVisible = true
        msg = '请先登录'
      }
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/**
 * 发起支付宝支付：新窗口打开支付宝收银台
 */
export async function openAlipayPay(orderId) {
  const data = await api.post(`/alipay/pay/${orderId}`)
  const payForm = data && data.payForm
  if (!payForm) {
    throw new Error('未获取到支付表单')
  }
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.warning('浏览器拦截了弹出窗口，请允许弹窗后重试')
    return
  }
  win.document.open()
  win.document.write(payForm)
  win.document.close()
  ElMessage.success('已打开支付宝收银台，请在新窗口完成支付（沙箱买家账号登录支付）')
}

export default api
