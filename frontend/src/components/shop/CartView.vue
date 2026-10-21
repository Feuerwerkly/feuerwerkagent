<template>
  <div v-loading="loading" class="cart-view">
    <div class="head-row">
      <h3>🛒 我的购物车</h3>
      <el-button text type="primary" @click="refresh()">刷新</el-button>
    </div>

    <el-empty v-if="!loading && items.length === 0" description="购物车空空如也，去逛逛吧～">
      <el-button type="primary" @click="store.goHome()">去逛逛</el-button>
    </el-empty>

    <template v-if="items.length > 0">
      <div class="select-all">
        <el-checkbox v-model="allChecked" @change="toggleAll">全选</el-checkbox>
      </div>

      <div class="cart-list">
        <div v-for="item in items" :key="item.cartItemId" class="cart-item">
          <el-checkbox v-model="checkedMap[item.cartItemId]" @change="emitCheckout" />
          <img class="thumb" :src="item.mainImage" @click="store.goProduct(item.productId)" />
          <div class="info">
            <div class="name" @click="store.goProduct(item.productId)">{{ item.name }}</div>
            <div class="brief">¥{{ item.price }} / 件 · 库存 {{ item.stock }}</div>
          </div>
          <el-input-number v-model="item.quantity" :min="1" :max="item.stock" size="small"
                           @change="qty => changeQty(item, qty)" />
          <div class="subtotal">¥{{ item.subtotal }}</div>
          <el-button type="danger" text @click="removeItem(item)">删除</el-button>
        </div>
      </div>

      <div class="settle-bar">
        <span class="total">已选 <b>{{ checkedCount }}</b> 件商品，合计：
          <span class="amount">¥{{ checkedTotal }}</span>
        </span>
        <el-button type="danger" size="large" :disabled="checkedCount === 0" @click="checkout">
          去结算（{{ checkedCount }}）
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../shop/api'
import { shopStore as store } from '../../shop/store'

const emit = defineEmits(['checkout'])

const items = ref([])
const checkedMap = reactive({})
const loading = ref(false)

const checkedCount = computed(() =>
  items.value.filter(i => checkedMap[i.cartItemId] && i.status === 1).length)

const checkedTotal = computed(() => items.value
  .filter(i => checkedMap[i.cartItemId] && i.status === 1)
  .reduce((sum, i) => sum + Number(i.subtotal || 0), 0)
  .toFixed(2))

const allChecked = computed({
  get: () => items.value.length > 0 && items.value.every(i => checkedMap[i.cartItemId]),
  set: () => { /* toggleAll处理 */ }
})

function toggleAll(val) {
  items.value.forEach(i => { checkedMap[i.cartItemId] = val })
}

async function refresh() {
  loading.value = true
  try {
    const data = await api.get('/cart')
    items.value = data || []
    items.value.forEach(i => {
      if (checkedMap[i.cartItemId] === undefined) {
        checkedMap[i.cartItemId] = true
      }
    })
    store.cartCount = items.value.length
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

async function changeQty(item, qty) {
  try {
    await api.put(`/cart/${item.cartItemId}`, { quantity: qty })
    item.subtotal = (Number(item.price) * qty).toFixed(2)
    item.quantity = qty
  } catch (e) {
    refresh()
  }
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm(`确定将「${item.name}」移出购物车吗？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api.delete(`/cart/${item.cartItemId}`)
    ElMessage.success('已移除')
    refresh()
  } catch (e) { /* 拦截器已提示 */ }
}

function emitCheckout() { /* 勾选变化仅影响本地合计 */ }

function checkout() {
  const checked = items.value
    .filter(i => checkedMap[i.cartItemId] && i.status === 1)
    .map(i => ({
      productId: i.productId,
      name: i.name,
      price: i.price,
      mainImage: i.mainImage,
      stock: i.stock,
      quantity: i.quantity
    }))
  if (!checked.length) return
  emit('checkout', checked)
}

defineExpose({ refresh })

onMounted(refresh)
</script>

<style scoped>
.cart-view {
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px;
}

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.head-row h3 { margin: 0; color: #333; }

.select-all {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.thumb {
  width: 88px;
  height: 66px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
}

.info { flex: 1; min-width: 0; }

.name {
  font-size: 14px;
  color: #333;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name:hover { color: #667eea; }

.brief { font-size: 12px; color: #999; margin-top: 6px; }

.subtotal {
  width: 90px;
  text-align: right;
  color: #e93b3b;
  font-weight: 600;
  font-size: 15px;
}

.settle-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 18px;
  margin-top: 16px;
}

.total { color: #666; font-size: 14px; }

.amount {
  color: #e93b3b;
  font-size: 24px;
  font-weight: 700;
}
</style>
