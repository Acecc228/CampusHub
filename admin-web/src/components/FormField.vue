<script setup lang="ts">
defineProps<{
  id: string;
  label: string;
  error?: string;
  help?: string;
  required?: boolean;
}>();
</script>
<template>
  <div class="field">
    <label :for="id"
      >{{ label
      }}<span v-if="required" class="required" aria-hidden="true"
        >*</span
      ></label
    >
    <slot :describedby="id + '-help'" :invalid="Boolean(error)" />
    <p
      :id="id + '-help'"
      :class="error ? 'field-error' : 'field-help'"
      :role="error ? 'alert' : undefined"
    >
      {{ error || help || "\u00a0" }}
    </p>
  </div>
</template>
