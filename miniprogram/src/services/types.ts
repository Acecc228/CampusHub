export interface Identity {
  id: number;
  loginName: string;
  displayName: string;
  roles: string[];
}
export interface Category {
  id: number;
  name: string;
}
export interface Registration {
  id: number | null;
  activityId: number;
  userId: number;
  status: "NONE" | "CONFIRMED" | "CANCELLED";
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
}
export interface Activity {
  id: number;
  title: string;
  summary: string;
  content?: string;
  categoryId: number;
  categoryName: string;
  location: string;
  startTime: string;
  endTime: string;
  registrationStart: string;
  registrationEnd: string;
  capacity: number;
  registeredCount: number;
  remainingCapacity: number;
  status: string;
  version: number;
  myRegistration?: Registration;
}
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
