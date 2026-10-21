<template>
  <el-dialog v-model="visible" title="确认订单" width="640px" align-center :close-on-click-modal="false">
    <!-- 商品清单 -->
    <div class="items">
      <div v-for="(item, i) in orderItems" :key="i" class="item">
        <img :src="item.mainImage" />
        <div class="info">
          <div class="name">{{ item.name }}</div>
          <div class="unit">¥{{ item.price }} × {{ item.quantity }}</div>
        </div>
        <div class="sub">¥{{ (Number(item.price) * item.quantity).toFixed(2) }}</div>
      </div>
    </div>

    <!-- 收货信息 -->
    <el-form :model="form" label-width="80px" style="margin-top: 14px">
      <el-form-item label="收货人" required>
        <el-input v-model="form.receiverName" placeholder="收货人姓名" />
      </el-form-item>
      <el-form-item label="手机号" required>
        <el-input v-model="form.receiverPhone" placeholder="收货手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="收货地址" required>
        <el-input v-model="form.receiverAddress" type="textarea" :rows="2" placeholder="省/市/区 + 详细地址" />
      </el-form-item>
      <el-form-item label="订单备注">
        <el-input v-model="form.remark" placeholder="选填" maxlength="200" />
      </el-form-item>
    </el-form>

    <div class="total-row">
      合计：<span class="amount">¥{{ total }}</span>
    </div>

    <template #footer>
      <el-button @click="visible = false">再想想</el-button>
      <el-button type="danger" :loading="submitting" @click="submit">提交订单</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../shop/api'

const emit = defineEmits(['order-created'])

const visible = ref(false)
const submitting = ref(false)
const orderItems = ref([])
const fromCart = ref(false)

// 记住收货信息，下次自动填充
const saved = JSON.parse(localStorage.getItem('shop_receiver') || 'null')

const form = reactive({
  receiverName: saved?.receiverName || '',
  receiverPhone: saved?.receiverPhone || '',
  receiverAddress: saved?.receiverAddress || '',
  remark: ''
})

const total = computed(() => orderItems.value
  .reduce((sum, i) => sum + Number(i.price) * i.quantity, 0)
  .toFixed(2))

function open(items, isFromCart) {
  orderItems.value = items
  fromCart.value = !!isFromCart
  visible.value = true
}

async function submit() {
  if (!form.receiverName.trim() || !form.receiverPhone.trim() || !form.receiverAddress.trim()) {
    ElMessage.warning('请完整填写收货人、手机号和地址')
    return
  }
  submitting.value = true
  try {
    const order = await api.post('/orders', {
      items: orderItems.value.map(i => ({ productId: i.productId, quantity: i.quantity })),
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress,
      remark: form.remark,
      fromCart: fromCart.value
    })
    localStorage.setItem('shop_receiver', JSON.stringify({
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress
    }))
    visible.value = false
    form.remark = ''
    emit('order-created', order)
  } catch (e) { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.items {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 6px 12px;
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.item:last-child { border-bottom: none; }

.item img {
  width: 64px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
}

.info { flex: 1; min-width: 0; }
.name { font-size: 13px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.unit { font-size: 12px; color: #999; margin-top: 4px; }
.sub { color: #e93b3b; font-weight: 600; }

.total-row {
  text-align: right;
  color: #666;
  font-size: 15px;
  margin-top: 8px;
}

.amount { color: #e93b3b; font-size: 24px; font-weight: 700; }
</style>
