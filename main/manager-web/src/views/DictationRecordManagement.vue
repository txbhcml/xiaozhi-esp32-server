<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">听写记录</h2>
              <div class="right-operations">
                <el-input
                  placeholder="按任务名称搜索"
                  v-model="searchKeyword"
                  class="search-input"
                  @keyup.enter="handleSearch"
                  clearable
                />
                <CustomButton icon="el-icon-search" type="confirm" size="small" @click="handleSearch">
                  搜索
                </CustomButton>
              </div>
            </div>
            <CustomTable
              ref="recordTable"
              :data="paramsList"
              :columns="tableColumns"
              :loading="loading"
              :show-operations="true"
              operations-label="操作"
              :total="total"
              :current-page="currentPage"
              :page-size="pageSize"
              :page-size-options="pageSizeOptions"
              @size-change="handlePageSizeChange"
              @page-change="goToPage"
            >
              <template #durationSeconds="scope">
                {{ formatDuration(scope.row.durationSeconds) }}
              </template>
              <template #operations="scope">
                <el-button size="small" link @click="handleViewDetail(scope.row)">查看详情</el-button>
                <el-button size="small" link type="danger" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <el-dialog
      title="听写记录详情"
      v-model="detailVisible"
      width="800px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-content">
        <el-descriptions :column="2" border size="small" v-if="detail">
          <el-descriptions-item label="任务名称">{{ detail.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备ID">{{ detail.deviceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单词总数">{{ detail.totalWords || 0 }}</el-descriptions-item>
          <el-descriptions-item label="听写时长">{{ formatDuration(detail.durationSeconds) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(detail.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(detail.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ formatTime(detail.createDate) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-words" v-if="detail && detail.words && detail.words.length">
          <div class="detail-words-title">本次播报单词快照（共 {{ detail.words.length }} 词）</div>
          <el-table :data="detail.words" border size="small" max-height="380">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="word" label="英文单词" min-width="140" />
            <el-table-column prop="meaning" label="中文释义" min-width="200" show-overflow-tooltip />
            <el-table-column prop="phoneticUs" label="美式音标" min-width="120" />
            <el-table-column prop="phoneticUk" label="英式音标" min-width="120" />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    <el-footer><VersionFooter /></el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import CustomButton from "@/components/CustomButton.vue";
import CustomTable from "@/components/CustomTable.vue";

export default {
  name: "DictationRecordManagement",
  components: { HeaderBar, VersionFooter, CustomButton, CustomTable },
  data() {
    return {
      searchKeyword: "",
      paramsList: [],
      currentPage: 1,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      loading: false,

      detailVisible: false,
      detailLoading: false,
      detail: null,

      tableColumns: []
    };
  },
  created() {
    this.initTableColumns();
    this.fetchRecordList();
  },
  methods: {
    initTableColumns() {
      this.tableColumns = [
        { prop: "taskName", label: "任务名称", align: "center", minWidth: 160 },
        { prop: "totalWords", label: "单词数", align: "center" },
        { prop: "durationSeconds", label: "听写时长", align: "center", slot: "durationSeconds" },
        { prop: "startTime", label: "开始时间", align: "center", minWidth: 160 },
        { prop: "endTime", label: "结束时间", align: "center", minWidth: 160 },
        { prop: "createDate", label: "创建时间", align: "center", minWidth: 160 }
      ];
    },

    fetchRecordList() {
      this.loading = true;
      Api.dictation.getRecordPage({
        page: this.currentPage,
        limit: this.pageSize,
        taskName: this.searchKeyword
      }, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.paramsList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.$message.error(data.msg || "获取听写记录列表失败");
        }
      });
    },

    handleSearch() {
      this.currentPage = 1;
      this.fetchRecordList();
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchRecordList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchRecordList();
      }
    },

    handleViewDetail(row) {
      this.detailVisible = true;
      this.detailLoading = true;
      this.detail = null;
      Api.dictation.getRecordDetail(row.id, ({ data }) => {
        this.detailLoading = false;
        if (data.code === 0) {
          this.detail = data.data;
        } else {
          this.$message.error(data.msg || "获取记录详情失败");
        }
      });
    },

    handleDelete(row) {
      this.$confirm(`确认删除该听写记录？`, "删除确认", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        Api.dictation.deleteRecord(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success("删除成功");
            this.fetchRecordList();
          } else {
            this.$message.error(data.msg || "删除失败");
          }
        });
      }).catch(() => {});
    },

    formatDuration(seconds) {
      if (seconds == null) return "-";
      const s = Number(seconds);
      if (isNaN(s) || s < 0) return "-";
      const m = Math.floor(s / 60);
      const sec = s % 60;
      return m > 0 ? `${m} 分 ${sec} 秒` : `${sec} 秒`;
    },

    formatTime(time) {
      if (!time) return "-";
      const d = new Date(time);
      if (isNaN(d.getTime())) return String(time);
      const pad = n => (n < 10 ? "0" + n : n);
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    }
  }
};
</script>

<style lang="scss" scoped>
.welcome {
  min-width: 900px;
  min-height: 506px;
  height: 100vh;
  display: flex;
  position: relative;
  flex-direction: column;
  background: #eff4ff;
  overflow: hidden;
}

.main-wrapper {
  height: calc(100vh - 63px - 35px);
  padding: 20px 22px 0;
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.operation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 16px 0;
  flex-wrap: wrap;
  gap: 10px;
}

.page-title {
  font-weight: 500;
  font-size: 24px;
  margin: 0;
}

.right-operations {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

.search-input {
  width: 240px;
}

.content-panel {
  display: flex;
  overflow: hidden;
  height: 100%;
  border-radius: 15px;
  background: transparent;
  border: 1px solid #fff;
}

.content-area {
  flex: 1;
  height: 100%;
  min-width: 600px;
  overflow: auto;
  background-color: white;
  display: flex;
  flex-direction: column;
}

.params-card {
  background: white;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: none;
  box-shadow: none;
  overflow: hidden;

  :deep(.el-card__body) {
    padding: 14px 20px;
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
  }
}

.detail-content {
  min-height: 200px;
}

.detail-words {
  margin-top: 16px;
}

.detail-words-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}
</style>
