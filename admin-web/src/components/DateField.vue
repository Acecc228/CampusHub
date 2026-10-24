<script setup lang="ts">
import FormField from "./FormField.vue";
defineProps<{ id: string; label: string; error?: string }>();
const model = defineModel<string>({ required: true });
</script>
<template>
  <FormField
    :id="id"
    :label="label"
    :error="error"
    required
    help="北京时间 · YYYY-MM-DD HH:mm:ss"
    v-slot="field"
  >
    <input
      :id="id"
      v-model="model"
      type="text"
      placeholder="2026-11-01 09:00:00"
      :aria-invalid="field.invalid"
      :aria-describedby="field.describedby"
      autocomplete="off"
      maxlength="19"
    />
  </FormField>
</template>
