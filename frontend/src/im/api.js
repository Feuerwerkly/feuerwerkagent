import axios from 'axios'
import { ElMessage } from 'element-plus'
import { shopStore } from '../shop/store'

/**
 * IM REST API封装（自动带token、统一错误提示）
 */
const imApi = axios.create({ baseURL: '/api/im' })

imApi.interceptors.request.use(config => {
  if (shopStore.token) {
    config.headers.Authorization = 'Bearer ' + shopStore.token
  }
  return config
})

imApi.interceptors.response.use(
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

export default imApi
