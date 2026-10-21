<template>
  <div class="admin-panel">
    <!-- 统计卡片 -->
    <div class="stats" v-loading="statsLoading">
      <div class="stat-card">
        <div class="num">¥{{ stats.totalSales || '0.00' }}</div>
        <div class="label">总销售额（已支付）</div>
      </div>
      <div class="stat-card">
        <div class="num">{{ stats.paidOrderCount || 0 }}</div>
        <div class="label">已支付订单</div>
      </div>
      <div class="stat-card">
        <div class="num">{{ stats.pendingOrderCount || 0 }}</div>
        <div class="label">待支付订单</div>
      </div>
      <div class="stat-card">
        <div class="num">{{ stats.productCount || 0 }}</div>
        <div class="label">在售商品数</div>
      </div>
      <div class="stat-card">
        <div class="num">{{ stats.totalStock || 0 }}</div>
        <div class="label">总库存</div>
      </div>
    </div>

    <el-tabs v-model="tab" class="admin-tabs">
      <!-- 商品管理 -->
      <el-tab-pane label="商品管理" name="products">
        <div class="toolbar">
          <el-input v-model="pKeyword" placeholder="商品名称搜索" clearable style="width:240px" @keyup.enter="loadProducts(1)" />
          <el-button type="primary" @click="loadProducts(1)">搜索</el-button>
          <el-button type="success" @click="openEdit(null)">＋ 新增商品</el-button>
          <el-button @click="loadAll">刷新</el-button>
        </div>

        <el-table :data="products" v-loading="pLoading" stripe>
          <el-table-column label="主图" width="90">
            <template #default="{ row }">
              <img :src="row.mainImage" style="width:72px;height:54px;object-fit:cover;border-radius:6px" />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column label="价格" width="100">
            <template #default="{ row }"><span style="color:#e93b3b;font-weight:600">¥{{ row.price }}</span></template>
          </el-table-column>
          <el-table-column prop="stock" label="库存" width="80" />
          <el-table-column prop="sales" label="销量" width="80" />
          <el-table-column label="评分" width="90">
            <template #default="{ row }">{{ row.ratingAvg }}（{{ row.ratingCount }}）</template>
          </el-table-column>
          <el-table-column label="上架" width="80">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="val => toggleStatus(row, val)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="removeProduct(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="pTotal"
                         :page-size="10" :current-page="pPage" @current-change="loadProducts" />
        </div>
      </el-tab-pane>

      <!-- 订单管理 -->
      <el-tab-pane label="订单管理" name="orders">
        <div class="toolbar">
          <el-select v-model="oStatus" style="width:160px" @change="loadOrders(1)" clearable placeholder="全部状态">
            <el-option label="待支付" :value="10" />
            <el-option label="已支付" :value="20" />
            <el-option label="已发货" :value="30" />
            <el-option label="已完成" :value="40" />
            <el-option label="已取消" :value="50" />
          </el-select>
          <el-button type="primary" @click="loadOrders(1)">查询</el-button>
          <el-button @click="loadAll">刷新</el-button>
        </div>

        <el-table :data="orders" v-loading="oLoading" stripe>
          <el-table-column prop="orderNo" label="订单号" width="180" />
          <el-table-column prop="userId" label="用户ID" width="80" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }">¥{{ row.totalAmount }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 10 ? 'warning' : row.status === 50 ? 'info' : 'success'">
                {{ statusDesc(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="tradeNo" label="支付宝交易号" width="220" show-overflow-tooltip />
          <el-table-column prop="createTime" label="下单时间" width="170" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 20" size="small" type="success" @click="ship(row)">发货</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="oTotal"
                         :page-size="10" :current-page="oPage" @current-change="loadOrders" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 商品编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑商品' : '新增商品'" width="620px" align-center>
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="商品名称" required>
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="editForm.category" placeholder="如：笔记本电脑 / 智能手机 / 平板电脑 / 显卡 / 显示器" />
        </el-form-item>
        <el-form-item label="价格(¥)" required>
          <el-input-number v-model="editForm.price" :min="0.01" :precision="2" :step="1" />
          <span class="form-tip">现价（促销价）</span>
        </el-form-item>
        <el-form-item label="原价(¥)">
          <el-input-number v-model="editForm.originalPrice" :min="0" :precision="2" :step="1" />
          <span class="form-tip">划线原价，留空则不显示促销对比</span>
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="editForm.stock" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="商品简介">
          <el-input v-model="editForm.brief" maxlength="200" placeholder="一句话卖点，展示在商品卡片上" />
        </el-form-item>
        <el-form-item label="商品详情">
          <el-input v-model="editForm.detail" type="textarea" :rows="6"
                    placeholder="多行文本，支持换行，展示在商品详情页" />
        </el-form-item>
        <el-form-item label="商品主图">
          <div class="img-editor">
            <img v-if="editForm.mainImage" :src="editForm.mainImage" />
            <div class="btns">
              <el-upload :show-file-list="false" :http-request="doUpload" accept="image/*">
                <el-button size="small">上传图片</el-button>
              </el-upload>
              <el-button size="small" @click="autoImage">自动生成图集</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../shop/api'

const tab = ref('products')

const stats = ref({})
const statsLoading = ref(false)

const products = ref([])
const pKeyword = ref('')
const pPage = ref(1)
const pTotal = ref(0)
const pLoading = ref(false)

const orders = ref([])
const oStatus = ref(null)
const oPage = ref(1)
const oTotal = ref(0)
const oLoading = ref(false)

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null, name: '', category: '', price: 99.9, originalPrice: null, stock: 100,
  brief: '', detail: '', mainImage: '', images: '', status: 1
})

function statusDesc(s) {
  return { 10: '待支付', 20: '已支付', 30: '已发货', 40: '已完成', 50: '已取消' }[s] || '未知'
}

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await api.get('/admin/stats') || {}
  } catch (e) { /* 忽略 */ } finally {
    statsLoading.value = false
  }
}

async function loadProducts(p = 1) {
  pPage.value = p
  pLoading.value = true
  try {
    const data = await api.get('/admin/products', {
      params: { keyword: pKeyword.value || undefined, page: p, size: 10 }
    })
    products.value = data.records || []
    pTotal.value = Number(data.total || 0)
  } catch (e) { /* 拦截器已提示 */ } finally {
    pLoading.value = false
  }
}

async function loadOrders(p = 1) {
  oPage.value = p
  oLoading.value = true
  try {
    const data = await api.get('/admin/orders', {
      params: { status: oStatus.value ?? undefined, page: p, size: 10 }
    })
    orders.value = data.records || []
    oTotal.value = Number(data.total || 0)
  } catch (e) { /* 拦截器已提示 */ } finally {
    oLoading.value = false
  }
}

function loadAll() {
  loadStats()
  loadProducts(pPage.value)
  loadOrders(oPage.value)
}

async function toggleStatus(row, val) {
  try {
    await api.put(`/admin/products/${row.id}/status`, null, { params: { status: val ? 1 : 0 } })
    row.status = val ? 1 : 0
    ElMessage.success(val ? '已上架' : '已下架')
    loadStats()
  } catch (e) { /* 拦截器已提示 */ }
}

async function removeProduct(row) {
  try {
    await ElMessageBox.confirm(`确定删除商品「${row.name}」吗？其评价也会被删除`, '删除商品', { type: 'error' })
  } catch (e) {
    return
  }
  try {
    await api.delete(`/admin/products/${row.id}`)
    ElMessage.success('已删除')
    loadAll()
  } catch (e) { /* 拦截器已提示 */ }
}

function openEdit(row) {
  if (row) {
    Object.assign(editForm, {
      id: row.id, name: row.name, category: row.category,
      price: Number(row.price), originalPrice: row.originalPrice ? Number(row.originalPrice) : null,
      stock: row.stock,
      brief: row.brief, detail: row.detail,
      mainImage: row.mainImage, images: row.images, status: row.status
    })
  } else {
    Object.assign(editForm, {
      id: null, name: '', category: '', price: 99.9, originalPrice: null, stock: 100,
      brief: '', detail: '', mainImage: '', images: '', status: 1
    })
  }
  editVisible.value = true
}

/** 自动生成图集：新增后由后端生成，编辑时按商品id生成 */
function autoImage() {
  if (editForm.id) {
    const base = `/api/shop/image/p${editForm.id}-`
    editForm.mainImage = base + '1'
    editForm.images = `${base}1,${base}2,${base}3`
    ElMessage.success('已按商品生成图集')
  } else {
    editForm.mainImage = ''
    editForm.images = ''
    ElMessage.info('保存后将为该商品自动生成图集')
  }
}

async function doUpload({ file }) {
  try {
    const fd = new FormData()
    fd.append('file', file)
    const data = await api.post('/upload/image', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    editForm.mainImage = data.url
    editForm.images = data.url
    ElMessage.success('图片已上传')
  } catch (e) { /* 拦截器已提示 */ }
}

async function saveProduct() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写商品名称')
    return
  }
  saving.value = true
  try {
    if (editForm.id) {
      await api.put(`/admin/products/${editForm.id}`, { ...editForm })
    } else {
      await api.post('/admin/products', { ...editForm })
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    loadAll()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

async function ship(row) {
  try {
    await api.post(`/admin/orders/${row.id}/ship`)
    ElMessage.success('已发货')
    loadAll()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(loadAll)
</script>

<style scoped>
.form-tip { color: #b0b3bb; font-size: 12px; margin-left: 8px; }
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stat-card {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 12px;
  padding: 16px;
  text-align: center;
}

.stat-card .num { font-size: 24px; font-weight: 700; }
.stat-card .label { font-size: 12px; opacity: 0.85; margin-top: 4px; }

.admin-tabs {
  background: #fff;
  border-radius: 12px;
  padding: 12px 16px 20px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.pager { display: flex; justify-content: center; margin-top: 14px; }

.img-editor { display: flex; gap: 12px; align-items: center; }

.img-editor img {
  width: 120px;
  height: 90px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #eee;
}

.img-editor .btns { display: flex; flex-direction: column; gap: 8px; }
</style>
