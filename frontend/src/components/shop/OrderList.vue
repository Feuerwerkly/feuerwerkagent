<template>
  <div v-loading="loading" class="order-list">
    <div class="head-row">
      <h3>📦 我的订单</h3>
      <el-button text type="primary" @click="refresh()">刷新</el-button>
    </div>

    <el-empty v-if="!loading && orders.length === 0" description="还没有订单，去下一单吧～">
      <el-button type="primary" @click="store.goHome()">去逛逛</el-button>
    </el-empty>

    <div v-for="order in orders" :key="order.id" class="order-card">
      <div class="order-head">
        <span class="no">订单号：{{ order.orderNo }}</span>
        <span class="time">{{ order.createTime }}</span>
        <el-tag :type="statusType(order.status)" effect="dark" size="small">{{ order.statusDesc }}</el-tag>
      </div>

      <div class="order-items">
        <div v-for="item in order.items" :key="item.id" class="order-item">
          <img :src="item.productImage" @click="store.goProduct(item.productId)" />
          <div class="info">
            <div class="name" @click="store.goProduct(item.productId)">{{ item.productName }}</div>
            <div class="unit">¥{{ item.price }} × {{ item.quantity }}</div>
          </div>
          <div class="sub">¥{{ item.subtotal }}</div>
          <el-button v-if="canReview(order, item)" size="small" type="warning" plain
                     @click="openReview(order, item)">评价</el-button>
          <el-tag v-else-if="reviewedSet.has(reviewKey(order.id, item.productId))" size="small" type="info">
            已评价
          </el-tag>
        </div>
      </div>

      <div class="receiver" v-if="order.receiverName">
        🚚 {{ order.receiverName }} · {{ order.receiverPhone }} · {{ order.receiverAddress }}
      </div>

      <div class="order-foot">
        <div class="amount">
          实付：<b>¥{{ order.totalAmount }}</b>
          <span v-if="order.tradeNo" class="trade">支付宝交易号：{{ order.tradeNo }}</span>
        </div>
        <div class="actions">
          <template v-if="order.status === 10">
            <el-button type="danger" @click="pay(order)">💳 支付宝支付</el-button>
            <el-button :loading="queryingNo === order.orderNo" @click="queryPayStatus(order)">
              🔍 查询支付状态
            </el-button>
            <el-button plain @click="cancelOrder(order)">取消订单</el-button>
          </template>
          <el-button v-if="order.status === 30" type="success" @click="confirmOrder(order)">
            ✅ 确认收货
          </el-button>
          <el-button v-if="[20, 30, 40].includes(order.status)" text type="primary" @click="loadMyReviews">
            我的评价
          </el-button>
        </div>
      </div>
    </div>

    <!-- 我的评价列表 -->
    <el-dialog v-model="myReviewsVisible" title="我的评价" width="560px" align-center>
      <div v-if="myReviews.length === 0" style="color:#999;text-align:center;padding:20px">暂无评价</div>
      <div v-for="r in myReviews" :key="r.id" class="my-review">
        <div class="head">
          <span class="pname">{{ r.productName }}</span>
          <el-rate :model-value="r.rating" disabled size="small" />
        </div>
        <div class="text">{{ r.content }}</div>
        <div class="time">{{ r.createTime }}</div>
      </div>
    </el-dialog>

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewVisible" title="评价商品" width="500px" align-center>
      <div v-if="reviewTarget" class="review-form">
        <div class="p-info">
          <img :src="reviewTarget.productImage" />
          <span>{{ reviewTarget.productName }}</span>
        </div>
        <div class="rate-row">
          综合评分：<el-rate v-model="reviewForm.rating" show-text size="large" />
        </div>
        <el-input v-model="reviewForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit
                  placeholder="说说商品的使用感受吧～" />
        <el-button type="primary" style="width:100%;margin-top:12px" :loading="reviewSubmitting" @click="submitReview">
          提交评价
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { openAlipayPay } from '../../shop/api'
import { shopStore as store } from '../../shop/store'

const loading = ref(false)
const orders = ref([])
const queryingNo = ref('')
const reviewedSet = reactive(new Set())
let pollTimer = null

const myReviewsVisible = ref(false)
const myReviews = ref([])

const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewTarget = ref(null)
const reviewForm = reactive({ rating: 5, content: '' })

function statusType(status) {
  switch (status) {
    case 10: return 'warning'
    case 20: return 'primary'
    case 30: return 'success'
    case 40: return 'success'
    case 50: return 'info'
    default: return 'info'
  }
}

function reviewKey(orderId, productId) {
  return orderId + '-' + productId
}

function canReview(order, item) {
  return [20, 30, 40].includes(order.status) && !reviewedSet.has(reviewKey(order.id, item.productId))
}

async function refresh() {
  loading.value = true
  try {
    orders.value = await api.get('/orders') || []
    loadMyReviews(true)
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

async function loadMyReviews(silent) {
  try {
    myReviews.value = await api.get('/reviews/mine') || []
    reviewedSet.clear()
    myReviews.value.forEach(r => {
      if (r.orderId != null) {
        reviewedSet.add(reviewKey(r.orderId, r.productId))
      }
    })
    if (!silent) myReviewsVisible.value = true
  } catch (e) { /* 忽略 */ }
}

async function pay(order) {
  try {
    await openAlipayPay(order.id)
    startPolling(order.orderNo)
  } catch (e) { /* 拦截器已提示 */ }
}

/**
 * 支付后轮询查单（本地无公网回调时的兜底方案）
 */
function startPolling(orderNo) {
  stopPolling()
  let count = 0
  pollTimer = setInterval(async () => {
    count++
    try {
      const result = await api.get('/alipay/query', { params: { orderNo } })
      if (result && result.orderStatus !== 10) {
        ElMessage.success('支付成功！订单已更新')
        stopPolling()
        refresh()
        return
      }
    } catch (e) { /* 忽略 */ }
    if (count >= 10) {
      stopPolling()
      ElMessage.info('暂未查询到支付结果，可稍后点击"查询支付状态"')
    }
  }, 6000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function queryPayStatus(order) {
  queryingNo.value = order.orderNo
  try {
    const result = await api.get('/alipay/query', { params: { orderNo: order.orderNo } })
    if (result && result.orderStatus !== 10) {
      ElMessage.success('支付成功！订单已更新')
      refresh()
    } else {
      ElMessage.info('支付宝侧暂无成功交易记录，请先在收银台完成支付')
    }
  } catch (e) { /* 拦截器已提示 */ } finally {
    queryingNo.value = ''
  }
}

async function cancelOrder(order) {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${order.orderNo} 吗？库存将回滚。`, '取消订单', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api.post(`/orders/${order.id}/cancel`)
    ElMessage.success('订单已取消')
    refresh()
  } catch (e) { /* 拦截器已提示 */ }
}

async function confirmOrder(order) {
  try {
    await ElMessageBox.confirm('确认已收到商品吗？', '确认收货', { type: 'success' })
  } catch (e) {
    return
  }
  try {
    await api.post(`/orders/${order.id}/confirm`)
    ElMessage.success('确认收货成功，欢迎评价商品')
    refresh()
  } catch (e) { /* 拦截器已提示 */ }
}

function openReview(order, item) {
  reviewTarget.value = { ...item, orderId: order.id }
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewForm.content.trim()) {
    ElMessage.warning('请填写评价内容')
    return
  }
  reviewSubmitting.value = true
  try {
    await api.post('/reviews', {
      productId: reviewTarget.value.productId,
      orderId: reviewTarget.value.orderId,
      rating: reviewForm.rating,
      content: reviewForm.content
    })
    ElMessage.success('评价成功，感谢分享！')
    reviewVisible.value = false
    refresh()
  } catch (e) { /* 拦截器已提示 */ } finally {
    reviewSubmitting.value = false
  }
}

defineExpose({ refresh, startPolling })

onMounted(refresh)
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.order-list { display: flex; flex-direction: column; gap: 14px; }

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 14px 20px;
}

.head-row h3 { margin: 0; color: #333; }

.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 20px;
}

.order-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
}

.no { font-size: 13px; color: #666; font-weight: 600; }
.time { font-size: 12px; color: #999; flex: 1; }

.order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}

.order-item img {
  width: 72px;
  height: 54px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
}

.info { flex: 1; min-width: 0; }
.name { font-size: 14px; color: #333; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.name:hover { color: #667eea; }
.unit { font-size: 12px; color: #999; margin-top: 4px; }
.sub { color: #e93b3b; font-weight: 600; width: 90px; text-align: right; }

.receiver {
  font-size: 12px;
  color: #999;
  padding: 8px 0;
  border-top: 1px dashed #f0f0f0;
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.amount { color: #666; font-size: 14px; }
.amount b { color: #e93b3b; font-size: 20px; }
.trade { display: block; font-size: 11px; color: #bbb; margin-top: 2px; }

.my-review { padding: 10px 0; border-bottom: 1px dashed #f0f0f0; }
.my-review .head { display: flex; justify-content: space-between; align-items: center; }
.my-review .pname { font-size: 14px; color: #333; font-weight: 600; }
.my-review .text { color: #555; font-size: 13px; margin: 6px 0; line-height: 1.6; }
.my-review .time { color: #bbb; font-size: 12px; }

.review-form .p-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  font-size: 14px;
  color: #333;
}

.review-form .p-info img {
  width: 56px;
  height: 42px;
  object-fit: cover;
  border-radius: 6px;
}

.rate-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #666;
}
</style>
