<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">听写任务</h2>
              <div class="right-operations">
                <el-input
                  placeholder="按任务名称搜索"
                  v-model="searchKeyword"
                  class="search-input"
                  @keyup.enter="handleSearch"
                  clearable
                />
                <el-select
                  v-model="searchStatus"
                  placeholder="状态"
                  clearable
                  class="status-select"
                >
                  <el-option label="启用" :value="1" />
                  <el-option label="禁用" :value="0" />
                </el-select>
                <CustomButton icon="el-icon-search" type="confirm" size="small" @click="handleSearch">
                  搜索
                </CustomButton>
                <CustomButton type="add" icon="el-icon-plus" size="small" @click="handleAdd">
                  新建任务
                </CustomButton>
              </div>
            </div>
            <CustomTable
              ref="taskTable"
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
              <template #mode="scope">
                <el-tag size="small" :type="scope.row.mode === 'listen_en' ? 'primary' : 'success'">
                  {{ scope.row.mode === 'listen_en' ? '播报英文' : '播报中文' }}
                </el-tag>
              </template>
              <template #accent="scope">
                {{ scope.row.accent === 'uk' ? '英式' : '美式' }}
              </template>
              <template #introduceWords="scope">
                <el-tag size="small" :type="scope.row.introduceWords ? 'success' : 'info'">
                  {{ scope.row.introduceWords ? '是' : '否' }}
                </el-tag>
              </template>
              <template #status="scope">
                <el-tag size="small" :type="scope.row.status === 1 ? 'success' : 'info'">
                  {{ scope.row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
              <template #operations="scope">
                <el-button size="small" link @click="handleEdit(scope.row)">编辑</el-button>
                <el-button size="small" link @click="handleStatusToggle(scope.row)">
                  {{ scope.row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button size="small" link type="danger" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="900px"
      :close-on-click-modal="false"
      destroy-on-close
      @close="onDialogClose"
    >
      <el-form
        ref="taskForm"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="例如：三年级上册 Unit 1" maxlength="64" show-word-limit />
        </el-form-item>

        <el-form-item label="播报模式" prop="mode">
          <el-radio-group v-model="form.mode">
            <el-radio value="listen_en">播报英文单词</el-radio>
            <el-radio value="listen_cn">播报中文释义</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="口音" prop="accent">
          <el-radio-group v-model="form.accent">
            <el-radio value="us">美式</el-radio>
            <el-radio value="uk">英式</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="单词间隔(秒)" prop="intervalSeconds">
          <el-input-number v-model="form.intervalSeconds" :min="1" :max="60" :step="1" :precision="0" />
        </el-form-item>

        <el-form-item label="播报次数" prop="repeatCount">
          <el-input-number v-model="form.repeatCount" :min="1" :max="3" :step="1" :precision="0" />
          <span class="form-hint">每个单词重复播报 1~3 次</span>
        </el-form-item>

        <el-form-item label="语速" prop="speakRate">
          <el-slider v-model="form.speakRate" :min="-100" :max="100" :step="10" style="width: 320px" />
          <span class="form-hint">-100 慢 / 0 标准 / 100 快</span>
        </el-form-item>

        <el-divider content-position="left">单词介绍阶段</el-divider>

        <el-form-item label="介绍所有单词">
          <el-switch v-model="form.introduceWords" />
          <span class="form-hint">听写开始前，先介绍所有单词（含拼写、例句等）</span>
        </el-form-item>

        <el-form-item label="播报例句" v-if="form.introduceWords">
          <el-switch v-model="form.showExample" />
        </el-form-item>

        <el-form-item label="翻译例句" v-if="form.introduceWords && form.showExample">
          <el-switch v-model="form.exampleTranslate" />
        </el-form-item>

        <el-form-item label="提示近反义词" v-if="form.introduceWords">
          <el-switch v-model="form.showSynonym" />
        </el-form-item>

        <el-divider content-position="left">单词来源</el-divider>

        <el-form-item label="来源方式" prop="source">
          <el-radio-group v-model="form.source" @change="onSourceChange">
            <el-radio value="book">从词书选择</el-radio>
            <el-radio value="manual">手动输入</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 词书选择 -->
        <template v-if="form.source === 'book'">
          <el-form-item label="选择词书" prop="bookId">
            <el-select
              v-model="form.bookId"
              placeholder="请选择词书"
              filterable
              style="width: 320px"
              @change="onBookChange"
            >
              <el-option
                v-for="book in books"
                :key="book.id"
                :label="`${book.name}（共 ${book.totalWords} 词）`"
                :value="book.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="选择单词">
            <div class="word-picker">
              <div class="word-picker-toolbar">
                <el-input
                  v-model="bookWordKeyword"
                  placeholder="搜索单词"
                  style="width: 200px"
                  clearable
                  @keyup.enter="searchBookWords"
                />
                <el-button size="small" @click="searchBookWords">搜索</el-button>
                <span class="selected-count">已选 {{ selectedWordIds.length }} 词</span>
              </div>
              <el-table
                :data="bookWords"
                v-loading="bookWordsLoading"
                border
                size="small"
                max-height="320"
                @selection-change="onBookWordsSelectionChange"
                :row-key="row => row.id"
                ref="bookWordTable"
              >
                <el-table-column
                  type="selection"
                  reserve-selection
                  width="50"
                  align="center"
                />
                <el-table-column prop="word" label="单词" min-width="120" />
                <el-table-column prop="meaning" label="中文释义" min-width="180" show-overflow-tooltip />
                <el-table-column prop="phoneticUs" label="美式音标" min-width="120" />
              </el-table>
              <div class="word-picker-pagination">
                <el-pagination
                  background
                  layout="total, prev, pager, next"
                  :total="bookWordsTotal"
                  :current-page="bookWordsCurrentPage"
                  :page-size="bookWordsPageSize"
                  @current-change="onBookWordsPageChange"
                />
              </div>
            </div>
          </el-form-item>
        </template>

        <!-- 手动输入 -->
        <template v-if="form.source === 'manual'">
          <el-form-item label="单词列表">
            <div class="manual-words">
              <el-button size="small" type="primary" plain @click="addManualWord">+ 添加单词</el-button>
              <el-table :data="manualWords" border size="small" style="margin-top: 8px">
                <el-table-column label="序号" type="index" width="60" align="center" />
                <el-table-column label="英文单词" min-width="160">
                  <template #default="scope">
                    <el-input v-model="scope.row.word" placeholder="apple" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="中文释义" min-width="200">
                  <template #default="scope">
                    <el-input v-model="scope.row.meaning" placeholder="苹果" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                  <template #default="scope">
                    <el-button size="small" link type="danger" @click="removeManualWord(scope.$index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-form-item>
        </template>

        <el-divider content-position="left">状态</el-divider>

        <el-form-item label="启用状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
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
  name: "DictationTaskManagement",
  components: { HeaderBar, VersionFooter, CustomButton, CustomTable },
  data() {
    return {
      searchKeyword: "",
      searchStatus: "",
      paramsList: [],
      currentPage: 1,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      loading: false,

      dialogVisible: false,
      dialogTitle: "新建听写任务",
      saving: false,
      form: this.buildEmptyForm(),
      rules: {
        taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
        mode: [{ required: true, message: "请选择播报模式", trigger: "change" }],
        accent: [{ required: true, message: "请选择口音", trigger: "change" }],
        bookId: [{ required: true, message: "请选择词书", trigger: "change" }]
      },

      books: [],
      bookWordKeyword: "",
      bookWords: [],
      bookWordsLoading: false,
      bookWordsCurrentPage: 1,
      bookWordsPageSize: 20,
      bookWordsTotal: 0,
      selectedWordIds: [],
      selectedWordVoMap: {},

      manualWords: [],

      tableColumns: []
    };
  },
  created() {
    this.initTableColumns();
    this.fetchTaskList();
    this.fetchBooks();
  },
  methods: {
    buildEmptyForm() {
      return {
        id: undefined,
        taskName: "",
        mode: "listen_en",
        accent: "us",
        intervalSeconds: 5,
        repeatCount: 1,
        speakRate: 0,
        introduceWords: false,
        showExample: false,
        exampleTranslate: false,
        showSynonym: false,
        source: "book",
        bookId: undefined,
        statusBool: true
      };
    },

    initTableColumns() {
      this.tableColumns = [
        { prop: "taskName", label: "任务名称", align: "center", minWidth: 160 },
        { prop: "mode", label: "播报模式", align: "center", slot: "mode" },
        { prop: "accent", label: "口音", align: "center", slot: "accent" },
        { prop: "wordCount", label: "单词数", align: "center" },
        { prop: "introduceWords", label: "介绍单词", align: "center", slot: "introduceWords" },
        { prop: "status", label: "状态", align: "center", slot: "status" },
        { prop: "createDate", label: "创建时间", align: "center", minWidth: 160 }
      ];
    },

    fetchTaskList() {
      this.loading = true;
      Api.dictation.getTaskPage({
        page: this.currentPage,
        limit: this.pageSize,
        taskName: this.searchKeyword,
        status: this.searchStatus
      }, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.paramsList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.$message.error(data.msg || "获取听写任务列表失败");
        }
      });
    },

    fetchBooks() {
      Api.dictation.listBooks(({ data }) => {
        if (data.code === 0) {
          this.books = data.data || [];
        }
      });
    },

    fetchBookWords(resetSelection = false) {
      if (!this.form.bookId) {
        this.bookWords = [];
        this.bookWordsTotal = 0;
        return;
      }
      this.bookWordsLoading = true;
      Api.dictation.pageWordsByBook({
        bookId: this.form.bookId,
        word: this.bookWordKeyword,
        page: this.bookWordsCurrentPage,
        limit: this.bookWordsPageSize
      }, ({ data }) => {
        this.bookWordsLoading = false;
        if (data.code === 0) {
          this.bookWords = data.data.list || [];
          this.bookWordsTotal = data.data.total || 0;
          this.$nextTick(() => {
            this.restoreBookWordSelection();
          });
        } else {
          this.$message.error(data.msg || "获取词书单词失败");
        }
      });
    },

    restoreBookWordSelection() {
      if (!this.$refs.bookWordTable) return;
      this.bookWords.forEach(row => {
        if (row.id && this.selectedWordIds.includes(row.id)) {
          this.$refs.bookWordTable.toggleRowSelection(row, true);
        }
      });
    },

    onBookChange() {
      this.bookWordsCurrentPage = 1;
      this.bookWordKeyword = "";
      this.fetchBookWords();
    },

    searchBookWords() {
      this.bookWordsCurrentPage = 1;
      this.fetchBookWords();
    },

    onBookWordsPageChange(page) {
      this.bookWordsCurrentPage = page;
      this.fetchBookWords();
    },

    onBookWordsSelectionChange(selection) {
      const currentPageIds = this.bookWords.map(w => w.id).filter(Boolean);
      const keptIds = this.selectedWordIds.filter(id => !currentPageIds.includes(id));
      const newSelectedIds = selection.map(w => w.id).filter(Boolean);
      this.selectedWordIds = [...new Set([...keptIds, ...newSelectedIds])];
      selection.forEach(w => {
        if (w.id) this.selectedWordVoMap[w.id] = w;
      });
    },

    onSourceChange() {
      // 切换来源方式时不清空已选项，由提交时统一处理
    },

    addManualWord() {
      this.manualWords.push({ word: "", meaning: "" });
    },

    removeManualWord(index) {
      this.manualWords.splice(index, 1);
    },

    handleSearch() {
      this.currentPage = 1;
      this.fetchTaskList();
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchTaskList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchTaskList();
      }
    },

    handleAdd() {
      this.form = this.buildEmptyForm();
      this.manualWords = [];
      this.selectedWordIds = [];
      this.selectedWordVoMap = {};
      this.bookWords = [];
      this.bookWordsTotal = 0;
      this.bookWordsCurrentPage = 1;
      this.bookWordKeyword = "";
      this.dialogTitle = "新建听写任务";
      this.dialogVisible = true;
    },

    handleEdit(row) {
      this.dialogTitle = "编辑听写任务";
      Api.dictation.getTaskDetail(row.id, ({ data }) => {
        if (data.code !== 0) {
          this.$message.error(data.msg || "获取任务详情失败");
          return;
        }
        const detail = data.data;
        const hasBookSource = (detail.selectedWordIds && detail.selectedWordIds.length > 0) || detail.bookId;
        this.form = {
          id: detail.id,
          taskName: detail.taskName || "",
          mode: detail.mode || "listen_en",
          accent: detail.accent || "us",
          intervalSeconds: detail.intervalSeconds != null ? Number(detail.intervalSeconds) : 5,
          repeatCount: detail.repeatCount != null ? detail.repeatCount : 1,
          speakRate: detail.speakRate || 0,
          introduceWords: !!detail.introduceWords,
          showExample: !!detail.showExample,
          exampleTranslate: !!detail.exampleTranslate,
          showSynonym: !!detail.showSynonym,
          source: hasBookSource ? "book" : "manual",
          bookId: detail.bookId || undefined,
          statusBool: detail.status === 1
        };

        this.selectedWordIds = (detail.selectedWordIds || []).map(id => Number(id));
        this.selectedWordVoMap = {};
        this.manualWords = (detail.words || []).map(w => ({
          word: w.word || "",
          meaning: w.meaning || ""
        }));

        this.bookWords = [];
        this.bookWordsTotal = 0;
        this.bookWordsCurrentPage = 1;
        this.bookWordKeyword = "";

        this.dialogVisible = true;

        if (hasBookSource && this.form.bookId) {
          this.$nextTick(() => {
            this.fetchBookWords();
          });
        }
      });
    },

    handleDelete(row) {
      this.$confirm(`确认删除任务「${row.taskName}」？`, "删除确认", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        Api.dictation.deleteTask(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success("删除成功");
            this.fetchTaskList();
          } else {
            this.$message.error(data.msg || "删除失败");
          }
        });
      }).catch(() => {});
    },

    handleStatusToggle(row) {
      const next = row.status === 1 ? 0 : 1;
      Api.dictation.updateTaskStatus(row.id, next, ({ data }) => {
        if (data.code === 0) {
          this.$message.success(next === 1 ? "已启用" : "已禁用");
          this.fetchTaskList();
        } else {
          this.$message.error(data.msg || "状态更新失败");
        }
      });
    },

    handleSubmit() {
      this.$refs.taskForm.validate(valid => {
        if (!valid) return;

        if (this.form.source === "book") {
          if (this.selectedWordIds.length === 0) {
            this.$message.warning("请至少选择一个单词");
            return;
          }
        } else {
          const validManual = this.manualWords.filter(w => (w.word || "").trim() && (w.meaning || "").trim());
          if (validManual.length === 0) {
            this.$message.warning("请至少添加一个完整单词（英文+释义）");
            return;
          }
        }

        const payload = {
          id: this.form.id,
          taskName: this.form.taskName,
          mode: this.form.mode,
          accent: this.form.accent,
          intervalSeconds: this.form.intervalSeconds,
          repeatCount: this.form.repeatCount,
          speakRate: this.form.speakRate,
          introduceWords: this.form.introduceWords,
          showExample: this.form.showExample,
          exampleTranslate: this.form.exampleTranslate,
          showSynonym: this.form.showSynonym,
          status: this.form.statusBool ? 1 : 0
        };

        if (this.form.source === "book") {
          payload.bookId = this.form.bookId;
          payload.selectedWordIds = this.selectedWordIds;
          payload.words = [];
        } else {
          payload.bookId = null;
          payload.selectedWordIds = [];
          payload.words = this.manualWords
            .filter(w => (w.word || "").trim() && (w.meaning || "").trim())
            .map(w => ({ word: w.word.trim(), meaning: w.meaning.trim() }));
        }

        this.saving = true;
        Api.dictation.saveTask(payload, ({ data }) => {
          this.saving = false;
          if (data.code === 0) {
            this.$message.success(this.form.id ? "保存成功" : "创建成功");
            this.dialogVisible = false;
            this.fetchTaskList();
          } else {
            this.$message.error(data.msg || "保存失败");
          }
        });
      });
    },

    onDialogClose() {
      this.saving = false;
      if (this.$refs.taskForm) {
        this.$refs.taskForm.clearValidate();
      }
      if (this.$refs.bookWordTable) {
        this.$refs.bookWordTable.clearSelection();
      }
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
  width: 200px;
}

.status-select {
  width: 120px;
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

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.word-picker {
  width: 100%;
}

.word-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.selected-count {
  margin-left: auto;
  color: #5778ff;
  font-size: 13px;
}

.word-picker-pagination {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.manual-words {
  width: 100%;
}
</style>
