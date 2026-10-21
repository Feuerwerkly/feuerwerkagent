<template>
  <div v-loading="loading" class="product-detail">
    <div class="back" @click="store.goHome()">← 返回商品列表</div>

    <template v-if="product">
      <!-- 上半部分：图集 + 购买信息 -->
      <div class="top">
        <div class="gallery">
          <img class="main-img" :src="gallery[active] || product.mainImage" :alt="product.name" />
          <div class="thumbs">
            <img v-for="(img, i) in gallery" :key="i" :src="img" :class="{ active: active === i }"
                 @mouseenter="active = i" @click="active = i" />
          </div>
        </div>

        <div class="buy-panel">
          <h2 class="title">{{ product.name }}</h2>
          <div class="brief">{{ product.brief }}</div>
          <div class="price-box">
            <div class="price-main">
              <span class="price"><i>¥</i>{{ product.price }}</span>
              <del v-if="product.originalPrice" class="original">¥{{ product.originalPrice }}</del>
              <span v-if="discount" class="discount">{{ discount }}折</span>
              <span v-if="saved" class="saved">已省 ¥{{ saved }}</span>
            </div>
            <div class="price-side">
              <span class="promo-clock">⏰ 限时价 · {{ countdown }} 后恢复原价</span>
              <span class="sold-count">已售 {{ product.sales }} 件</span>
            </div>
          </div>
          <div class="rate-row">
            <el-rate :model-value="Number(product.ratingAvg) || 5" disabled />
            <span class="rate-text">{{ product.ratingAvg }} 分 · {{ product.ratingCount }} 条评价</span>
          </div>
          <div class="stock-row">
            <el-tag v-if="product.stock > 30" type="success" effect="plain">现货 · 库存 {{ product.stock }} 件</el-tag>
            <el-tag v-else-if="product.stock > 0" type="danger" effect="dark">手慢无 · 仅剩 {{ product.stock }} 件</el-tag>
            <el-tag v-else type="info" effect="dark">已售罄 · 补货中</el-tag>
            <el-tag effect="plain">18:00 前下单当日发</el-tag>
            <el-tag effect="plain" type="warning">顺丰包邮</el-tag>
          </div>
          <div class="service-row">
            <span>✓ 正品保障</span>
            <span>✓ 7天无理由退换</span>
            <span>✓ 全国联保</span>
            <span>✓ 晒单返 10 元红包</span>
          </div>

          <div class="qty-row">
            <span class="label">数量</span>
            <el-input-number v-model="quantity" :min="1" :max="Math.max(product.stock, 1)" />
          </div>

          <div class="btn-row">
            <el-button size="large" plain type="primary" :disabled="product.stock === 0" @click="addToCart">
              🛒 加入购物车
            </el-button>
            <el-button size="large" type="danger" :disabled="product.stock === 0" @click="buyNow">
              ⚡ 立即购买
            </el-button>
          </div>
        </div>
      </div>

      <!-- 下半部分：详情 + 评价 -->
      <el-tabs v-model="tab" class="bottom">
        <el-tab-pane label="商品详情" name="detail">
          <div class="detail-text">{{ product.detail || '暂无详情' }}</div>
        </el-tab-pane>
        <el-tab-pane :label="`商品评价(${product.ratingCount})`" name="reviews">
          <div class="review-summary">
            <span class="score">{{ product.ratingAvg }}</span>
            <el-rate :model-value="Number(product.ratingAvg) || 5" disabled />
            <span class="count">共 {{ reviewTotal }} 条评价（购买后可评价）</span>
          </div>
          <div v-if="reviews.length === 0" class="no-review">暂无评价，快来抢首评吧～</div>
          <div v-for="r in reviews" :key="r.id" class="review-item">
            <div class="avatar">{{ (r.nickname || '匿').slice(0, 1) }}</div>
            <div class="content">
              <div class="head">
                <span class="nick">{{ r.nickname }}</span>
                <el-rate :model-value="r.rating" disabled size="small" />
              </div>
              <div class="text">{{ r.content }}</div>
              <div v-if="reviewImages(r.images).length" class="imgs">
                <img v-for="(img, i) in reviewImages(r.images)" :key="i" :src="img" />
              </div>
              <div class="time">{{ r.createTime }}</div>
            </div>
          </div>
          <div class="pager" v-if="reviewTotal > reviewPageSize">
            <el-pagination background layout="prev, pager, next" :total="reviewTotal"
                           :page-size="reviewPageSize" :current-page="reviewPage" @current-change="loadReviews" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../shop/api'
import { shopStore as store } from '../../shop/store'

const props = defineProps({ productId: [Number, String] })
const emit = defineEmits(['buy-now'])

const product = ref(null)
const loading = ref(false)
const active = ref(0)
const quantity = ref(1)
const tab = ref('detail')

// 折扣 / 已省 / 限时倒计时（当天 23:59:59）
const discount = computed(() => {
  const p = product.value
  if (!p || !p.originalPrice || Number(p.originalPrice) <= Number(p.price)) return null
  return (Number(p.price) / Number(p.originalPrice) * 10).toFixed(1).replace(/\.0$/, '')
})
const saved = computed(() => {
  const p = product.value
  if (!p || !p.originalPrice) return null
  const diff = Number(p.originalPrice) - Number(p.price)
  return diff > 0 ? diff.toFixed(0) : null
})
const countdown = ref('--:--:--')
let timer = null
function tick() {
  const end = new Date()
  end.setHours(23, 59, 59, 0)
  const left = Math.max(0, end.getTime() - Date.now())
  const h = String(Math.floor(left / 3600000)).padStart(2, '0')
  const m = String(Math.floor(left % 3600000 / 60000)).padStart(2, '0')
  const s = String(Math.floor(left % 60000 / 1000)).padStart(2, '0')
  countdown.value = `${h}:${m}:${s}`
}

const reviews = ref([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewPageSize = 8

const gallery = computed(() => {
  if (!product.value) return []
  const list = product.value.images ? product.value.images.split(',').filter(Boolean) : []
  return list.length ? list : [product.value.mainImage]
})

async function load() {
  if (!props.productId) return
  loading.value = true
  active.value = 0
  quantity.value = 1
  try {
    product.value = await api.get(`/products/${props.productId}`)
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
  reviewPage.value = 1
  loadReviews(1)
}

async function loadReviews(p = 1) {
  reviewPage.value = p
  try {
    const data = await api.get(`/reviews/product/${props.productId}`, { params: { page: p, size: reviewPageSize } })
    reviews.value = data.reviews || []
    reviewTotal.value = Number(data.total || 0)
  } catch (e) { /* 忽略 */ }
}

function reviewImages(images) {
  return images ? images.split(',').filter(Boolean) : []
}

async function addToCart() {
  if (!store.requireLogin()) return
  try {
    await api.post('/cart', { productId: product.value.id, quantity: quantity.value })
    store.cartCount++
    ElMessage.success('已加入购物车')
  } catch (e) { /* 拦截器已提示 */ }
}

function buyNow() {
  if (!store.requireLogin()) return
  emit('buy-now', product.value, quantity.value)
}

watch(() => props.productId, () => load())
onMounted(load)
onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.back {
  color: #667eea;
  cursor: pointer;
  margin-bottom: 12px;
  font-size: 14px;
  width: fit-content;
}

.top {
  display: flex;
  gap: 24px;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  flex-wrap: wrap;
}

.gallery { flex: 0 0 420px; }

.main-img {
  width: 420px;
  max-width: 100%;
  aspect-ratio: 4/3;
  object-fit: cover;
  border-radius: 10px;
  background: #f5f6fa;
}

.thumbs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.thumbs img {
  width: 76px;
  height: 57px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.thumbs img.active { border-color: #667eea; }

.buy-panel { flex: 1; min-width: 300px; }

.title { font-size: 22px; color: #222; margin-bottom: 8px; }

.brief {
  color: #999;
  font-size: 14px;
  margin-bottom: 16px;
  line-height: 1.6;
}

.price-box {
  background: linear-gradient(90deg, #fff4f4, #fff);
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.price-main { display: flex; align-items: baseline; gap: 10px; }

.price {
  color: #e93b3b;
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
}

.price i { font-style: normal; font-size: 18px; margin-right: 2px; }

.original { color: #b8b8b8; font-size: 14px; }

.discount {
  font-size: 12px;
  color: #e93b3b;
  border: 1px solid #ffc9c9;
  background: #fff3f3;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.saved {
  font-size: 12px;
  color: #c45656;
  background: #ffecec;
  padding: 1px 8px;
  border-radius: 10px;
}

.price-side { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.promo-clock { color: #e93b3b; font-size: 12px; font-weight: 600; }
.sold-count { color: #999; font-size: 12px; }

.service-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #8f9aa8;
  font-size: 12px;
  margin: -8px 0 16px;
  padding: 8px 12px;
  background: #f8f9fb;
  border-radius: 6px;
}

.rate-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}

.rate-text { font-size: 13px; color: #999; }

.stock-row { display: flex; gap: 8px; margin-bottom: 20px; flex-wrap: wrap; }

.qty-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.qty-row .label { color: #666; font-size: 14px; }

.btn-row { display: flex; gap: 12px; }

.bottom {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px 24px;
  margin-top: 16px;
}

.detail-text {
  white-space: pre-wrap;
  line-height: 1.9;
  color: #444;
  font-size: 14px;
}

.review-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 14px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 6px;
}

.review-summary .score {
  font-size: 28px;
  font-weight: 700;
  color: #ff9900;
}

.review-summary .count { color: #999; font-size: 13px; }

.no-review { color: #bbb; text-align: center; padding: 30px 0; }

.review-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.content { flex: 1; }

.head { display: flex; align-items: center; gap: 10px; }

.nick { font-size: 13px; color: #666; }

.text { margin: 6px 0; color: #333; font-size: 14px; line-height: 1.6; }

.imgs { display: flex; gap: 8px; flex-wrap: wrap; }

.imgs img { width: 80px; height: 80px; object-fit: cover; border-radius: 6px; }

.time { color: #bbb; font-size: 12px; }

.pager { display: flex; justify-content: center; margin-top: 14px; }
</style>
