<template>
  <!-- ==================== 小米式首页模式 ==================== -->
  <div v-if="mode === 'home'" class="mi-home">
    <!-- 轮播 Banner + 左侧分类竖栏 -->
    <div class="hero-zone">
      <aside class="cat-menu">
        <div v-for="c in FLOOR_ORDER" :key="c" class="cat-row" @click="goFloor(c)">
          <span>{{ c }}</span><i>›</i>
        </div>
      </aside>

      <div class="carousel">
        <div v-for="(s, i) in slides" v-show="slide === i" :key="i" class="slide" :class="s.cls">
          <div class="slide-text">
            <h3>{{ s.title }}</h3>
            <p>{{ s.sub }}</p>
            <button class="slide-btn" @click="goCategoryList(s.category)">{{ s.btn }} →</button>
          </div>
          <img :src="s.img" alt="" class="slide-img" />
        </div>
        <button class="car-arrow left" @click="slide = (slide + slides.length - 1) % slides.length">‹</button>
        <button class="car-arrow right" @click="slide = (slide + 1) % slides.length">›</button>
        <div class="car-dots">
          <span v-for="(s, i) in slides" :key="i" :class="{ on: slide === i }" @click="slide = i"></span>
        </div>
      </div>
    </div>

    <!-- 限时秒杀 -->
    <section class="section">
      <div class="sec-head">
        <h2>限时秒杀</h2>
        <div class="flash-clock">
          <span>距结束</span>
          <em>{{ countdown }}</em>
        </div>
      </div>
      <div class="flash-grid">
        <div v-for="p in flashSale" :key="p.id" class="flash-card" @click="openProduct(p)">
          <div class="flash-img">
            <img :src="p.mainImage" alt="" />
            <span v-if="p.stock === 0" class="sold-mask"><b>已售罄</b></span>
          </div>
          <div class="flash-name">{{ p.name }}</div>
          <div class="flash-brief">{{ p.brief }}</div>
          <div class="flash-price">
            <em>¥{{ p.price }}</em>
            <del v-if="p.originalPrice">¥{{ p.originalPrice }}</del>
          </div>
          <div class="flash-bar">
            <i :style="{ width: soldPct(p) + '%' }"></i>
            <span>已抢 {{ soldPct(p) }}%</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 品类楼层 -->
    <section v-for="c in FLOOR_ORDER" :id="'floor-' + c" :key="c" class="section floor">
      <div class="sec-head">
        <h2>{{ c }}</h2>
        <span class="sec-sub">{{ FLOOR_SLOGAN[c] || '精选好物' }}</span>
        <a class="sec-more" @click.prevent="goCategoryList(c)">查看全部 ›</a>
      </div>
      <div class="floor-body">
        <div class="floor-poster" :class="FLOOR_THEME[c]" @click="goCategoryList(c)">
          <h4>{{ FLOOR_POSTER[c]?.title || c + '专区' }}</h4>
          <p>{{ FLOOR_POSTER[c]?.sub || '爆款直降中' }}</p>
          <img v-if="posterOf(c)" :src="posterOf(c)" alt="" />
          <span class="poster-btn">进入专区 ›</span>
        </div>
        <div class="floor-grid">
          <div v-for="p in groupBy[c] || []" :key="p.id" class="mi-card"
               :class="{ soldout: p.stock === 0 }" @click="openProduct(p)">
            <div class="mi-img">
              <img :src="p.mainImage" alt="" loading="lazy" />
              <span v-if="p.sales >= 2600 && p.stock > 0" class="corner-badge">爆款</span>
              <span v-else-if="isNew(p)" class="corner-badge new">新品</span>
              <span v-if="p.stock === 0" class="sold-mask"><b>已售罄</b><i>补货中</i></span>
            </div>
            <div class="mi-name">{{ p.name }}</div>
            <div class="mi-brief">{{ p.brief }}</div>
            <div class="mi-price">
              <em>¥{{ p.price }}</em>
              <del v-if="p.originalPrice">¥{{ p.originalPrice }}</del>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="mi-footer">
      <div class="service-row">
        <div class="svc"><b>✓</b>正品保障</div>
        <div class="svc"><b>✓</b>顺丰包邮</div>
        <div class="svc"><b>✓</b>7天无理由退换</div>
        <div class="svc"><b>✓</b>全国联保</div>
      </div>
      <div class="foot-line">Feuerwerk 数码商城 · 演示项目 · 支付宝沙箱支付 · © 2026</div>
    </footer>
  </div>

  <!-- ==================== 列表模式（分类/搜索） ==================== -->
  <div v-else class="mi-list">
    <div class="list-wrap">
      <!-- 面包屑 + 结果数 -->
      <div class="crumb">
        <a @click.prevent="backHome">首页</a>
        <span>›</span>
        <b v-if="keyword">“{{ keyword }}” 的搜索结果（{{ total }} 件）</b>
        <b v-else>{{ category }}（{{ total }} 件）</b>
        <span v-if="keyword || category !== '全部'" class="crumb-clear" @click="clearFilter">清除筛选 ×</span>
      </div>

      <!-- 排序条 -->
      <div class="sort-bar">
        <button v-for="s in SORTS" :key="s.value" :class="{ on: sort === s.value }"
                @click="sort = s.value; load(1)">{{ s.label }}</button>
        <el-input v-model="keyword" placeholder="搜索商品名称/简介..." clearable size="small"
                  style="width: 220px; margin-left: auto" @keyup.enter="load(1)" @clear="load(1)">
          <template #append><el-button :icon="Search" @click="load(1)" /></template>
        </el-input>
      </div>

      <!-- 商品网格 -->
      <div v-loading="loading" class="list-grid">
        <div v-for="p in products" :key="p.id" class="mi-card tall"
             :class="{ soldout: p.stock === 0 }" @click="openProduct(p)">
          <div class="mi-img">
            <img :src="p.mainImage" alt="" loading="lazy" />
            <span v-if="p.sales >= 2600 && p.stock > 0" class="corner-badge">爆款</span>
            <span v-else-if="isNew(p)" class="corner-badge new">新品</span>
            <span v-if="p.stock === 0" class="sold-mask"><b>已售罄</b><i>补货中</i></span>
          </div>
          <div class="mi-name">{{ p.name }}</div>
          <div class="mi-brief">{{ p.brief }}</div>
          <div class="mi-meta">已售 {{ p.sales }} 件 · {{ p.stock > 0 ? (p.stock <= 30 ? `仅剩 ${p.stock} 件` : '现货') : '已售罄' }}</div>
          <div class="mi-price">
            <em>¥{{ p.price }}</em>
            <del v-if="p.originalPrice">¥{{ p.originalPrice }}</del>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && products.length === 0" description="没有找到相关商品" />

      <div class="pager">
        <el-pagination background layout="prev, pager, next, total" :total="total"
                       :page-size="pageSize" :current-page="page" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { Search } from '@element-plus/icons-vue'
import api from '../../shop/api'
import { shopStore } from '../../shop/store'

const emit = defineEmits(['buy-now'])

// ---------- 常量 ----------
const FLOOR_ORDER = ['智能手机', '笔记本电脑', '平板电脑', '显卡', '显示器', '智能穿戴', '智能音频']
const FLOOR_SLOGAN = {
  '智能手机': '旗舰影像 · 极致性能',
  '笔记本电脑': '轻至无感 · 强至无敌',
  '平板电脑': '大屏生产力 · 随行娱乐',
  '显卡': '游戏战未来 · AI 生产力',
  '显示器': '4K/MiniLED 视界',
  '智能穿戴': '14天续航 · 专业运动',
  '智能音频': '静界之声 · 一戴倾心'
}
const FLOOR_POSTER = {
  '智能手机': { title: 'iPhone 16 Pro', sub: '沙漠色钛金属 · 相机控制键' },
  '笔记本电脑': { title: 'MacBook Air M5', sub: '18小时续航 · 1.13kg' },
  '平板电脑': { title: 'iPad Pro M4', sub: '史上最薄 Apple 设备' },
  '显卡': { title: 'RTX 50 系上市', sub: 'DLSS 4 多帧生成' },
  '显示器': { title: 'MiniLED 时代', sub: 'HDR1000 千元档' },
  '智能穿戴': { title: 'WATCH GT 5 Pro', sub: '钛金属 · 自由潜泳' },
  '智能音频': { title: 'AirPods Pro 2', sub: '2倍主动降噪' }
}
const FLOOR_THEME = {
  '智能手机': 't-orange', '笔记本电脑': 't-blue', '平板电脑': 't-purple',
  '显卡': 't-dark', '显示器': 't-teal', '智能穿戴': 't-green', '智能音频': 't-pink'
}
const SORTS = [
  { label: '最新上架', value: 'new' },
  { label: '销量优先', value: 'sales' },
  { label: '价格从低到高', value: 'priceAsc' },
  { label: '价格从高到低', value: 'priceDesc' }
]

// 轮播 slides（图片动态绑定，避免构建期被当模块解析）
const slides = [
  { title: 'RTX 50 系显卡登场', sub: 'DLSS 4 多帧生成 · 4K 光追旗舰之王', btn: '抢购显卡', category: '显卡', img: '/api/shop/image/blob/rtx-5090', cls: 's-dark' },
  { title: 'iPhone 16 Pro', sub: '沙漠色钛金属 · A18 Pro · 相机控制', btn: '了解更多', category: '智能手机', img: '/api/shop/image/blob/iphone-16-pro', cls: 's-warm' },
  { title: 'MacBook Air M5', sub: '1.13kg · 18小时续航 · 无风扇静音', btn: '立即选购', category: '笔记本电脑', img: '/api/shop/image/blob/macbook-air', cls: 's-blue' }
]

// ---------- 状态 ----------
const mode = ref('home')          // home 首页楼层 / list 列表
const all = ref([])               // 首页全量商品
const keyword = ref('')
const category = ref('全部')
const sort = ref('new')
const products = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(false)

const slide = ref(0)
let carTimer = null

// ---------- 首页数据 ----------
async function loadHome() {
  try {
    const data = await api.get('/products', { params: { size: 100, sort: 'sales' } })
    all.value = data.records || []
  } catch (e) { /* 拦截器已提示 */ }
}

const groupBy = computed(() => {
  const map = {}
  for (const p of all.value) {
    (map[p.category] = map[p.category] || []).push(p)
  }
  return map
})

// 秒杀：销量前4
const flashSale = computed(() => all.value.slice(0, 4))

const posterOf = (c) => (groupBy.value[c] || [])[0]?.mainImage

const isNew = (p) => {
  if (!p.createTime) return false
  return (Date.now() - new Date(p.createTime).getTime()) / 86400000 <= 10
}
const soldPct = (p) => {
  const total_ = (p.sales || 0) + (p.stock || 0)
  if (total_ <= 0) return 50
  return Math.min(96, Math.max(12, Math.round((p.sales / total_) * 100)))
}

// ---------- 列表数据 ----------
async function load(p = 1) {
  page.value = p
  loading.value = true
  try {
    const data = await api.get('/products', {
      params: {
        keyword: keyword.value || undefined,
        category: category.value === '全部' ? undefined : category.value,
        sort: sort.value,
        page: p,
        size: pageSize
      }
    })
    products.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function goCategoryList(c) {
  mode.value = 'list'
  category.value = c
  keyword.value = ''
  load(1)
}

function backHome() {
  mode.value = 'home'
  loadHome()
}

function clearFilter() {
  keyword.value = ''
  category.value = '全部'
  load(1)
}

// 楼层锚点
function goFloor(c) {
  mode.value === 'home' || backHome()
  nextTick(() => {
    document.getElementById('floor-' + c)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

// 顶部导航信号（分类）
watch(() => shopStore.navSeq, () => {
  const c = shopStore.navCategory
  if (c && c !== '全部') {
    mode.value = 'list'
    category.value = c
    keyword.value = ''
    load(1)
  } else {
    mode.value = 'home'
    loadHome()
  }
})

// 顶部导航信号（搜索）
watch(() => shopStore.searchSeq, () => {
  mode.value = 'list'
  category.value = '全部'
  keyword.value = shopStore.searchKeyword || ''
  load(1)
})

function openProduct(p) {
  if (p.stock === 0) return
  shopStore.goProduct(p.id)
}

// ---------- 倒计时 ----------
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

onMounted(async () => {
  // 恢复最近一次导航状态：从详情页返回时停在对应分类列表
  if (shopStore.navCategory && shopStore.navCategory !== '全部') {
    mode.value = 'list'
    category.value = shopStore.navCategory
    if (shopStore.searchKeyword) keyword.value = shopStore.searchKeyword
    load(1)
  } else if (shopStore.searchKeyword) {
    mode.value = 'list'
    keyword.value = shopStore.searchKeyword
    load(1)
  } else {
    loadHome()
  }
  tick()
  timer = setInterval(tick, 1000)
  carTimer = setInterval(() => {
    slide.value = (slide.value + 1) % slides.length
  }, 4500)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (carTimer) clearInterval(carTimer)
})
</script>

<style scoped>
/* ==================== 公共 ==================== */
.mi-home, .mi-list { background: #f5f5f5; min-height: 60vh; }
.section, .list-wrap, .mi-footer .foot-inner {
  max-width: 1226px;
  margin: 0 auto;
  padding: 0 10px;
}

/* ==================== 首页 Banner 区 ==================== */
.hero-zone {
  max-width: 1226px;
  margin: 0 auto;
  padding: 10px 10px 0;
  display: flex;
  gap: 10px;
  position: relative;
}

/* 左侧分类竖栏（小米式深色） */
.cat-menu {
  width: 190px;
  flex-shrink: 0;
  background: rgba(30, 30, 38, 0.92);
  padding: 14px 0;
  border-radius: 4px;
}

.cat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #d7d7d7;
  font-size: 14px;
  padding: 11px 24px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.cat-row:hover { background: #ff6700; color: #fff; }
.cat-row i { font-style: normal; opacity: 0.6; }

/* 轮播 */
.carousel {
  flex: 1;
  position: relative;
  height: 380px;
  border-radius: 4px;
  overflow: hidden;
}

.slide {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 64px;
  animation: fadeIn 0.5s;
}

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.s-dark { background: linear-gradient(115deg, #1b1b24 30%, #32325a); }
.s-warm { background: linear-gradient(115deg, #6b4b2a 20%, #c49a6c); }
.s-blue { background: linear-gradient(115deg, #1d3a5f 20%, #4a7fc1); }

.slide-text { color: #fff; max-width: 46%; z-index: 1; }
.slide-text h3 { font-size: 34px; font-weight: 700; margin-bottom: 12px; }
.slide-text p { font-size: 15px; opacity: 0.82; margin-bottom: 22px; line-height: 1.6; }

.slide-btn {
  background: #ff6700;
  color: #fff;
  border: none;
  padding: 10px 28px;
  border-radius: 3px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.slide-btn:hover { background: #f25d00; }

.slide-img {
  height: 84%;
  max-width: 46%;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.4);
}

.car-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 60px;
  border: none;
  background: rgba(0, 0, 0, 0.18);
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  transition: background 0.2s;
  z-index: 2;
}

.car-arrow:hover { background: rgba(0, 0, 0, 0.42); }
.car-arrow.left { left: 0; border-radius: 0 4px 4px 0; }
.car-arrow.right { right: 0; border-radius: 4px 0 0 4px; }

.car-dots {
  position: absolute;
  right: 16px;
  bottom: 14px;
  display: flex;
  gap: 8px;
  z-index: 2;
}

.car-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: all 0.2s;
}

.car-dots span.on { background: #fff; width: 18px; border-radius: 4px; }

/* ==================== 区块通用 ==================== */
.section { margin-top: 26px; }

.sec-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.sec-head h2 { font-size: 22px; color: #333; font-weight: 700; }
.sec-sub { font-size: 13px; color: #b0b0b0; }
.sec-more { margin-left: auto; color: #666; font-size: 13px; cursor: pointer; }
.sec-more:hover { color: #ff6700; }

/* ==================== 秒杀 ==================== */
.flash-clock {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #999;
  background: #fff;
  border-radius: 4px;
  padding: 4px 10px;
}

.flash-clock em {
  font-style: normal;
  color: #ff6700;
  font-family: Consolas, monospace;
  font-size: 15px;
  font-weight: 700;
}

.flash-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.flash-card {
  background: #fff;
  border-radius: 4px;
  padding: 18px 14px 14px;
  cursor: pointer;
  text-align: center;
  transition: transform 0.2s, box-shadow 0.2s;
}

.flash-card:hover { transform: translateY(-3px); box-shadow: 0 12px 26px rgba(0, 0, 0, 0.09); }

.flash-img { position: relative; height: 130px; }
.flash-img img { height: 100%; object-fit: contain; }

.flash-name {
  font-size: 14px;
  color: #333;
  margin-top: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flash-brief {
  font-size: 12px;
  color: #b0b0b0;
  margin: 4px 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flash-price em { font-style: normal; color: #ff6700; font-size: 18px; font-weight: 700; }
.flash-price del { color: #b0b0b0; font-size: 12px; margin-left: 6px; }

.flash-bar {
  position: relative;
  margin-top: 8px;
  height: 18px;
  background: #f5e6d8;
  border-radius: 9px;
  overflow: hidden;
}

.flash-bar i {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(90deg, #ff6700, #ff9a3c);
  border-radius: 9px;
}

.flash-bar span {
  position: relative;
  z-index: 1;
  font-size: 11px;
  color: #7a4a12;
  line-height: 18px;
  font-weight: 600;
}

/* ==================== 楼层 ==================== */
.floor-body {
  display: flex;
  gap: 10px;
}

.floor-poster {
  width: 232px;
  flex-shrink: 0;
  border-radius: 4px;
  color: #fff;
  padding: 26px 18px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s;
}

.floor-poster:hover { transform: translateY(-2px); }
.floor-poster h4 { font-size: 21px; font-weight: 700; }
.floor-poster p { font-size: 12px; opacity: 0.82; margin: 8px 0 14px; }
.floor-poster img {
  width: 100%;
  height: 130px;
  object-fit: contain;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
}
.poster-btn { margin-top: auto; font-size: 13px; opacity: 0.9; }

.t-orange { background: linear-gradient(160deg, #ff8a3c, #ff6700); }
.t-blue { background: linear-gradient(160deg, #4a7fc1, #2d5fa8); }
.t-purple { background: linear-gradient(160deg, #8a6fd1, #5f4a9e); }
.t-dark { background: linear-gradient(160deg, #3c3c46, #1f1f28); }
.t-teal { background: linear-gradient(160deg, #2fa39a, #17736d); }
.t-green { background: linear-gradient(160deg, #52b788, #2d8659); }
.t-pink { background: linear-gradient(160deg, #e58bb0, #c05c8c); }

.floor-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(226px, 1fr));
  gap: 10px;
}

/* ==================== 小米式商品卡 ==================== */
.mi-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px 12px 14px;
  cursor: pointer;
  text-align: center;
  transition: transform 0.2s, box-shadow 0.2s;
}

.mi-card:hover { transform: translateY(-3px); box-shadow: 0 14px 28px rgba(0, 0, 0, 0.08); }
.mi-card.soldout { cursor: not-allowed; opacity: 0.72; }
.mi-card.soldout:hover { transform: none; box-shadow: none; }

.mi-img {
  position: relative;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mi-img img { height: 100%; object-fit: contain; }

.corner-badge {
  position: absolute;
  top: 0;
  left: 8px;
  background: linear-gradient(90deg, #ff4d4f, #ff7849);
  color: #fff;
  font-size: 11px;
  padding: 2px 9px;
  border-radius: 0 0 6px 6px;
  font-weight: 600;
}

.corner-badge.new { background: linear-gradient(90deg, #34c57b, #5dd6a2); }

.sold-mask {
  position: absolute;
  inset: 0;
  background: rgba(100, 100, 110, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #fff;
  border-radius: 4px;
}

.sold-mask b { font-size: 17px; letter-spacing: 3px; }
.sold-mask i { font-style: normal; font-size: 11px; opacity: 0.85; }

.mi-name {
  font-size: 14px;
  color: #333;
  margin-top: 10px;
  line-height: 1.4;
  height: 2.8em;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  text-align: center;
}

.mi-brief {
  font-size: 12px;
  color: #b0b0b0;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mi-meta { font-size: 11px; color: #c0c0c0; margin-top: 4px; }

.mi-price { margin-top: 8px; }
.mi-price em { font-style: normal; color: #ff6700; font-size: 17px; font-weight: 700; }
.mi-price del { color: #b0b0b0; font-size: 12px; margin-left: 6px; }

/* ==================== 列表页 ==================== */
.list-wrap { padding-top: 14px; }

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #999;
  padding: 8px 0 14px;
}

.crumb a { color: #666; cursor: pointer; }
.crumb a:hover { color: #ff6700; }
.crumb b { color: #333; font-weight: 600; }

.crumb-clear {
  margin-left: 10px;
  color: #ff6700;
  cursor: pointer;
  font-size: 12px;
}

.sort-bar {
  display: flex;
  gap: 4px;
  align-items: center;
  background: #fff;
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 10px;
}

.sort-bar button {
  border: none;
  background: none;
  color: #666;
  font-size: 13px;
  padding: 7px 16px;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.15s;
}

.sort-bar button:hover { color: #ff6700; }
.sort-bar button.on { background: #ff6700; color: #fff; }

.list-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(226px, 1fr));
  gap: 10px;
  min-height: 200px;
}

.pager {
  display: flex;
  justify-content: center;
  margin: 22px 0 30px;
}

/* ==================== 页脚 ==================== */
.mi-footer {
  background: #fff;
  margin-top: 30px;
  padding: 26px 0 20px;
}

.foot-inner { max-width: 1226px; margin: 0 auto; padding: 0 10px; }

.service-row {
  display: flex;
  justify-content: space-between;
  padding-bottom: 18px;
  border-bottom: 1px solid #eee;
}

.svc { color: #666; font-size: 15px; display: flex; align-items: center; gap: 8px; }
.svc b {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #ff6700;
  color: #fff;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.foot-line {
  text-align: center;
  color: #bbb;
  font-size: 12px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .cat-menu { display: none; }
  .slide-text h3 { font-size: 24px; }
  .flash-grid { grid-template-columns: repeat(2, 1fr); }
  .floor-body { flex-direction: column; }
  .floor-poster { width: 100%; flex-direction: row; align-items: center; }
  .floor-poster img { width: 120px; height: 90px; }
}
</style>
