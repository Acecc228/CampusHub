export type Role = "ADMIN" | "ORGANIZER" | "STUDENT";
export interface Identity {
  id: number;
  loginName: string;
  displayName: string;
  roles: Role[];
}
export type ActivityStatus = "DRAFT" | "PUBLISHED" | "UNPUBLISHED";
export interface ActivityInput {
  title: string;
  summary: string;
  content: string;
  categoryId: number;
  location: string;
  startTime: string;
  endTime: string;
  registrationStart: string;
  registrationEnd: string;
  capacity: number;
  version?: number;
}
export interface Activity extends ActivityInput {
  id: number;
  categoryName: string;
  creatorId: number;
  status: ActivityStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  registeredCount: number;
  remainingCapacity: number;
}
export interface Category {
  id: number;
  code: string;
  name: string;
  sortOrder: number;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Registration {
  id: number;
  activityId: number;
  userId: number;
  status: "CONFIRMED" | "CANCELLED";
  version: number;
  createdAt: string;
  updatedAt: string;
}
