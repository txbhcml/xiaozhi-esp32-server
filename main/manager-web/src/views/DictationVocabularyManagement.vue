<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">词书标熟</h2>
              <div class="right-operations">
                <el-select
                  v-model="selectedBookId"
                  placeholder="不选则显示所有标熟单词"
                  filterable
                  clearable
                  class="book-select"
                  @change="onBookChange"
                >
                  <el-option
                    v-for="book in books"
                    :key="book.id"
                    :label="`${book.name}（共 ${book.totalWords} 词）`"
                    :value="book.id"
                  />
                </el-select>
                <el-input
                  placeholder="搜索单词"
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
              ref="wordTable"
              :data="wordList"
              :columns="tableColumns"
              :loading="loading"
              :show-operations="true"
              operations-label="操作"
              :operations-width="120"
              :total="total"
              :current-page="currentPage"
              :page-size="pageSize"
              :page-size-options="pageSizeOptions"
              @size-change="handlePageSizeChange"
              @page-change="goToPage"
            >
              <template #familiarStatus="scope">
                <el-tag size="small" :type="scope.row.familiarId ? 'success' : 'info'">
                  {{ scope.row.familiarId ? '已标熟' : '未标熟' }}
                </el-tag>
              </template>
              <template #operations="scope">
                <el-button
                  v-if="!scope.row.familiarId"
                  size="small"
                  link
                  type="primary"
                  :loading="markingId === scope.row.id"
                  @click="handleMark(scope.row)"
                >标熟</el-button>
                <el-button
                  v-else
                  size="small"
                  link
                  type="warning"
                  :loading="markingId === scope.row.id"
                  @click="handleUnmark(scope.row)"
                >取消标熟</el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>
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
  name: "DictationVocabularyManagement",
  components: { HeaderBar, VersionFooter, CustomButton, CustomTable },
  data() {
    return {
      books: [],
      selectedBookId: undefined,
      searchKeyword: "",
      wordList: [],
      currentPage: 1,
      pageSize: 20,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      loading: false,
      markingId: null,
      tableColumns: []
    };
  },
  created() {
    this.initTableColumns();
    this.fetchBooks();
    // 进入页面时未选词书，默认展示所有标熟单词
    this.fetchWordList();
  },
  methods: {
    initTableColumns() {
      // 未选词书时（跨词书标熟列表）展示"所属词书"列
      const columns = [
        { prop: "word", label: "单词", align: "center", minWidth: 140 },
        { prop: "meaning", label: "中文释义", align: "center", minWidth: 220 },
        { prop: "phoneticUs", label: "美式音标", align: "center", minWidth: 140 }
      ];
      if (!this.selectedBookId) {
        columns.push({ prop: "bookName", label: "所属词书", align: "center", minWidth: 160 });
      }
      columns.push({
        prop: "familiarStatus",
        label: "标熟状态",
        align: "center",
        slot: "familiarStatus",
        showOverflowTooltip: false
      });
      this.tableColumns = columns;
    },

    fetchBooks() {
      Api.dictation.listBooks(({ data }) => {
        if (data.code === 0) {
          this.books = data.data || [];
        } else {
          this.$message.error(data.msg || "获取词书列表失败");
        }
      });
    },

    fetchWordList() {
      this.loading = true;
      if (this.selectedBookId) {
        // 选了词书：查该词书全部单词（含标熟状态，可标熟/取消标熟）
        Api.dictation.pageWordsWithFamiliar({
          bookId: this.selectedBookId,
          word: this.searchKeyword,
          page: this.currentPage,
          limit: this.pageSize
        }, ({ data }) => {
          this.loading = false;
          if (data.code === 0) {
            this.wordList = data.data.list || [];
            this.total = data.data.total || 0;
          } else {
            this.$message.error(data.msg || "获取词书单词失败");
          }
        });
      } else {
        // 没选词书：查当前用户所有标熟单词（跨词书）
        Api.dictation.pageFamiliarWords({
          word: this.searchKeyword,
          page: this.currentPage,
          limit: this.pageSize
        }, ({ data }) => {
          this.loading = false;
          if (data.code === 0) {
            this.wordList = data.data.list || [];
            this.total = data.data.total || 0;
          } else {
            this.$message.error(data.msg || "获取标熟单词失败");
          }
        });
      }
    },

    onBookChange() {
      this.currentPage = 1;
      this.searchKeyword = "";
      this.initTableColumns();
      this.fetchWordList();
    },

    handleSearch() {
      this.currentPage = 1;
      this.fetchWordList();
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchWordList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchWordList();
      }
    },

    handleMark(row) {
      this.markingId = row.id;
      Api.dictation.markFamiliar({
        vocabId: row.id,
        word: row.word,
        bookId: this.selectedBookId
      }, ({ data }) => {
        this.markingId = null;
        if (data.code === 0) {
          this.$message.success("已标熟");
          this.fetchWordList();
        } else {
          this.$message.error(data.msg || "标熟失败");
        }
      });
    },

    handleUnmark(row) {
      if (!row.familiarId) return;
      this.markingId = row.id;
      Api.dictation.unmarkFamiliar(row.familiarId, ({ data }) => {
        this.markingId = null;
        if (data.code === 0) {
          this.$message.success("已取消标熟");
          this.fetchWordList();
        } else {
          this.$message.error(data.msg || "取消标熟失败");
        }
      });
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

.book-select {
  width: 280px;
}

.search-input {
  width: 200px;
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
</style>
