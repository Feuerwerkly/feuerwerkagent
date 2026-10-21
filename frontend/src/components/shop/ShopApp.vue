<template>
  <div class="shop-app">
    <!-- 小米式黑色顶条 -->
    <div class="topbar">
      <div class="topbar-inner">
        <div class="topbar-left">
          <span class="topbar-brand">Feuerwerk 数码精选 · 让科技更好玩</span>
        </div>
        <div class="topbar-right">
          <template v-if="store.isLoggedIn()">
            <a @click.prevent>{{ store.user.nickname || store.user.username }}</a>
            <span class="sep"></span>
            <a @click.prevent="goOrders">我的订单</a>
            <span class="sep"></span>
            <a @click.prevent="goCart" class="cart-link">
              🛒 购物车
              <em v-if="store.cartCount > 0" class="cart-n">{{ store.cartCount }}</em>
            </a>
            <template v-if="store.isAdmin()">
              <span class="sep"></span>
              <a @click.prevent="store.view = 'admin'" class="admin-link">商品管理</a>
            </template>
            <span class="sep"></span>
            <a @click.prevent="logout" class="quiet">退出</a>
          </template>
          <template v-else>
            <a @click.prevent="store.loginVisible = true">登录 / 注册</a>
            <span class="sep"></span>
            <a @click.prevent="goOrders" class="quiet">我的订单</a>
            <span class="sep"></span>
            <a @click.prevent="goCart" class="cart-link">
              🛒 购物车
              <em v-if="store.cartCount > 0" class="cart-n">{{ store.cartCount }}</em>
            </a>
          </template>
        </div>
      </div>
    </div>

    <!-- 小米式白色主导航：logo + 分类 + 搜索 -->
    <div class="main-nav">
      <div class="main-nav-inner">
        <div class="logo" @click="store.goHome()">
          <span class="logo-mark">F</span>
          <span class="logo-text">Feuerwerk<em>商城</em></span>
        </div>
        <nav class="nav-cats">
          <a :class="{ on: isHomeNav('全部') }" @click.prevent="navTo('全部')">首页</a>
          <a v-for="c in NAV_CATEGORIES" :key="c" :class="{ on: isHomeNav(c) }" @click.prevent="navTo(c)">{{ c }}</a>
        </nav>
        <div class="nav-search">
          <input v-model="kw" placeholder="搜索商品，例如：iPhone / 5090" @keyup.enter="doSearch" />
          <button @click="doSearch">🔍</button>
        </div>
      </div>
    </div>

    <!-- 页面区域 -->
    <div class="shop-body">
      <ProductList v-if="store.view === 'home'" @buy-now="onBuyNow" />
      <ProductDetail v-else-if="store.view === 'product'" :product-id="store.productId" @buy-now="onBuyNow" />
      <CartView v-else-if="store.view === 'cart'" ref="cartRef" @checkout="onCheckout" />
      <OrderList v-else-if="store.view === 'orders'" ref="orderRef" />
      <AdminPanel v-else-if="store.view === 'admin'" />
    </div>

    <!-- 下单确认弹窗（购物车结算 / 立即购买共用） -->
    <!-- 登录弹窗已全局化至 App.vue -->
    <OrderConfirmDialog ref="confirmRef" @order-created="onOrderCreated" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { openAlipayPay } from '../../shop/api'
import { shopStore as store } from '../../shop/store'
import OrderConfirmDialog from './OrderConfirmDialog.vue'
import ProductList from './ProductList.vue'
import ProductDetail from './ProductDetail.vue'
import CartView from './CartView.vue'
import OrderList from './OrderList.vue'
import AdminPanel from './AdminPanel.vue'

const cartRef = ref(null)
const orderRef = ref(null)
const confirmRef = ref(null)
const kw = ref('')

// 主导航分类（与商品库分类保持一致）
const NAV_CATEGORIES = ['智能手机', '笔记本电脑', '平板电脑', '显卡', '显示器', '智能穿戴', '智能音频']

function isHomeNav(c) {
  // 首页模式下当前高亮：'全部'即首页
  return store.view === 'home' && store.navCategory === c
}

function navTo(c) {
  if (c === '全部') {
    store.goHome()
  } else {
    store.goCategoryList(c)
  }
}

function doSearch() {
  store.goCategoryList('全部')
  store.searchKeyword = kw.value
  store.searchSeq = (store.searchSeq || 0) + 1
}

// 购物车角标
async function refreshCartCount() {
  if (!store.isLoggedIn()) {
    store.cartCount = 0
    return
  }
  try {
    store.cartCount = await api.get('/cart/count')
  } catch (e) { /* 忽略 */ }
}

function goCart() {
  if (!store.requireLogin()) return
  store.view = 'cart'
}

function goOrders() {
  if (!store.requireLogin()) return
  store.view = 'orders'
}

// 登录/退出后刷新购物车角标（登录弹窗已全局化，通过token变化感知）
watch(() => store.token, () => {
  refreshCartCount()
  if (store.isAdmin() && store.view === 'home') {
    ElMessage.info('欢迎回来，店主！可点击顶栏"商品管理"维护商品')
  }
})

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api.post('/auth/logout')
  } catch (e) { /* 忽略 */ }
  store.clearAuth()
  ElMessage.success('已退出登录')
}

// 立即购买
function onBuyNow(product, quantity) {
  if (!store.requireLogin()) return
  confirmRef.value.open([{
    productId: product.id,
    name: product.name,
    price: product.price,
    mainImage: product.mainImage,
    stock: product.stock,
    quantity
  }], false)
}

// 购物车结算
function onCheckout(checkedItems) {
  confirmRef.value.open(checkedItems, true)
}

// 下单成功 -> 询问是否立即支付
async function onOrderCreated(order) {
  refreshCartCount()
  store.view = 'orders'
  await nextTick()
  try {
    await ElMessageBox.confirm(
      `下单成功！订单号 ${order.orderNo}，应付金额 ¥${order.totalAmount}，是否立即用支付宝（沙箱）支付？`,
      '去支付',
      { confirmButtonText: '立即支付', cancelButtonText: '稍后支付', type: 'success' }
    )
    await openAlipayPay(order.id)
    if (orderRef.value) {
      orderRef.value.startPolling(order.orderNo)
    }
  } catch (e) { /* 稍后支付 */ }
}

onMounted(() => {
  refreshCartCount()
  // 支付宝同步回跳：?payReturn=1&out_trade_no=xxx
  const params = new URLSearchParams(window.location.search)
  if (params.get('payReturn') === '1') {
    store.view = 'orders'
    const orderNo = params.get('out_trade_no')
    if (orderNo && store.isLoggedIn()) {
      ElMessage.info('正在查询支付结果...')
      setTimeout(async () => {
        try {
          await api.get('/alipay/query', { params: { orderNo } })
        } catch (e) { /* 忽略 */ }
        if (orderRef.value) orderRef.value.refresh()
      }, 500)
    }
  }
})
</script>

<style scoped>
.shop-app {
  background: #fff;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.shop-body {
  flex: 1;
}

/* ===== 黑色顶条 ===== */
.topbar {
  background: #333;
  color: #b0b0b0;
  font-size: 12px;
}

.topbar-inner {
  max-width: 1226px;
  margin: 0 auto;
  padding: 0 10px;
  height: 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.topbar-brand { color: #9d9d9d; letter-spacing: 0.5px; }

.topbar-right { display: flex; align-items: center; gap: 0; }

.topbar-right a {
  color: #b0b0b0;
  cursor: pointer;
  padding: 0 8px;
  line-height: 40px;
  transition: color 0.15s;
}

.topbar-right a:hover { color: #fff; }
.topbar-right a.quiet:hover { color: #ff6700; }

.sep {
  width: 1px;
  height: 12px;
  background: #4c4c4c;
}

.cart-link { position: relative; }
.cart-n {
  position: absolute;
  top: 2px;
  right: -4px;
  background: #ff6700;
  color: #fff;
  font-style: normal;
  font-size: 10px;
  min-width: 14px;
  height: 14px;
  line-height: 14px;
  text-align: center;
  border-radius: 7px;
  padding: 0 2px;
}

.admin-link { color: #ffd479 !important; }

/* ===== 白色主导航 ===== */
.main-nav {
  background: #fff;
  border-bottom: 1px solid #eee;
  position: sticky;
  top: 0;
  z-index: 50;
}

.main-nav-inner {
  max-width: 1226px;
  margin: 0 auto;
  padding: 0 10px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}

.logo-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: #ff6700;
  color: #fff;
  font-size: 22px;
  font-weight: 800;
  font-family: Georgia, serif;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  letter-spacing: 0.5px;
}

.logo-text em {
  font-style: normal;
  color: #ff6700;
  margin-left: 2px;
}

.nav-cats {
  display: flex;
  gap: 2px;
  flex: 1;
  overflow-x: auto;
}

.nav-cats a {
  color: #333;
  font-size: 14px;
  padding: 6px 10px;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s;
}

.nav-cats a:hover { color: #ff6700; }
.nav-cats a.on { color: #ff6700; font-weight: 600; }

.nav-search {
  display: flex;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
  transition: border-color 0.2s;
}

.nav-search:focus-within { border-color: #ff6700; }

.nav-search input {
  border: none;
  outline: none;
  width: 200px;
  padding: 8px 12px;
  font-size: 13px;
}

.nav-search button {
  border: none;
  background: #fff;
  border-left: 1px solid #e0e0e0;
  padding: 0 14px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.nav-search button:hover {
  background: #ff6700;
  color: #fff;
}

@media (max-width: 900px) {
  .nav-cats { display: none; }
  .nav-search input { width: 120px; }
}
</style>
