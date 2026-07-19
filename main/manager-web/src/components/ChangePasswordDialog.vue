<template>
  <form>
    <CustomDialog
      :title="$t('changePassword.title')"
      :visible="modelValue" @update:visible="val => $emit('update:modelValue', val)"
      width="600px"
      @cancel="cancel"
      @confirm="confirm">
      <div class="password-form">
        <div class="form-label">
          <span class="required">*</span>
          {{ $t('changePassword.oldPasswordLabel') }}
        </div>
        <div class="input-46" style="margin-top: 12px;">
          <el-input :placeholder="$t('changePassword.oldPasswordPlaceholder')" v-model="oldPassword" type="password" show-password />
        </div>
        <div class="form-label" style="margin-top: 12px;">
          <span class="required">*</span>
          {{ $t('changePassword.newPasswordLabel') }}
        </div>
        <div class="input-46" style="margin-top: 12px;">
          <el-input :placeholder="$t('changePassword.newPasswordPlaceholder')" v-model="newPassword" type="password" show-password />
        </div>
        <div class="form-label" style="margin-top: 12px;">
          <span class="required">*</span>
          {{ $t('changePassword.confirmPasswordLabel') }}
        </div>
        <div class="input-46" style="margin-top: 12px;">
          <el-input :placeholder="$t('changePassword.confirmPasswordPlaceholder')" v-model="confirmNewPassword" type="password" show-password />
        </div>
      </div>
    </CustomDialog>
  </form>
</template>

<script>
import userApi from '@/apis/module/user';
import { mapActions } from 'vuex';
import CustomDialog from '@/components/CustomDialog.vue';

export default {
  name: 'ChangePasswordDialog',
  props: {
    modelValue: {
      type: Boolean,
      required: true
    }
  },
  emits: ['update:modelValue'],
  data() {
    return {
      oldPassword: "",
      newPassword: "",
      confirmNewPassword: ""
    }
  },
  components: {
    CustomDialog
  },
  methods: {
    ...mapActions(['logout']),
    confirm() {
      if (!this.oldPassword.trim() || !this.newPassword.trim() || !this.confirmNewPassword.trim()) {
        this.$message.error(this.$t('changePassword.allFieldsRequired'));
        return;
      }
      if (this.newPassword !== this.confirmNewPassword) {
        this.$message.error(this.$t('changePassword.passwordsNotMatch'));
        return;
      }
      if (this.newPassword === this.oldPassword) {
        this.$message.error(this.$t('changePassword.newPasswordSameAsOld'));
        return;
      }

      userApi.changePassword(this.oldPassword, this.newPassword, (res) => {
        if (res.data.code === 0) {
          this.$message.success({
            message: this.$t('changePassword.passwordChangedSuccessfully'),
            showClose: true
          });
          this.logout().then(() => {
            this.$router.push('/login');
          });
        } else {
          this.$message.error(res.data.msg || this.$t('changePassword.changeFailed'));
        }
      }, (err) => {
        this.$message.error(err.msg || this.$t('changePassword.changeFailed'));
      });
      this.$emit('update:modelValue', false);
    },
    cancel() {
      this.resetForm();
    },
    resetForm() {
      this.oldPassword = "";
      this.newPassword = "";
      this.confirmNewPassword = "";
    }
  }
}
</script>

<style scoped>

.form-label {
  text-align: left;
}

.required {
  color: red;
  display: inline-block;
}

.input-46 {
  background: #f6f8fb;
  border-radius: 15px;
}
</style>
