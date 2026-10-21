import { reactive } from 'vue'

/**
 * 商店全局状态
 */
export const shopStore = reactive({
  token: localStorage.getItem('shop_token') || '',
  user: JSON.parse(localStorage.getItem('shop_user') || 'null'),
  cartCount: 0,
  loginVisible: false,
  // 当前页面：home首页 product商品详情 cart购物车 orders我的订单 admin管理
  view: 'home',
  productId: null,
  // 顶部导航跳转列表页的信号（seq 自增触发 ProductList watch）
  navCategory: '全部',
  navSeq: 0,

  isLoggedIn() {
    return !!this.token
  },

  isAdmin() {
    return this.user && this.user.role === 'ADMIN'
  },

  setAuth(user, token) {
    this.user = user
    this.token = token
    localStorage.setItem('shop_user', JSON.stringify(user))
    localStorage.setItem('shop_token', token)
  },

  clearAuth() {
    this.user = null
    this.token = ''
    this.cartCount = 0
    localStorage.removeItem('shop_user')
    localStorage.removeItem('shop_token')
  },

  goHome() {
    this.view = 'home'
    this.productId = null
    // 首页即全部（回到楼层首页），并清空搜索词
    this.navCategory = '全部'
    this.searchKeyword = ''
  },

  /**
   * 顶部导航跳分类列表（首页楼层/导航条共用）
   */
  goCategoryList(category) {
    this.navCategory = category
    this.navSeq++
    this.view = 'home'
    this.productId = null
  },

  goProduct(id) {
    this.productId = id
    this.view = 'product'
  },

  requireLogin() {
    if (!this.isLoggedIn()) {
      this.loginVisible = true
      return false
    }
    return true
  }
})
