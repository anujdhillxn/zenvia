import React from "react";

export type User = {
    username: string;
    email: string;
    invitationToken: string;
};

export type Duo = {
    user1: string;
    user2: string;
    createdAt: string;
};

export type RuleModificationData = {
    app: string;
    interventionType: string;
    dailyReset: string;
    isActive: boolean;
    dailyMaxSeconds: number;
    hourlyMaxSeconds: number;
    sessionMaxSeconds: number;
    isDailyMaxSecondsEnforced: boolean;
    isHourlyMaxSecondsEnforced: boolean;
    isSessionMaxSecondsEnforced: boolean;
    isStartupDelayEnabled: boolean;
};

export type Rule = {
    app: string;
    appDisplayName: string;
    isActive: boolean;
    isMyRule: boolean;
    interventionType: string;
    dailyReset: string;
    dailyMaxSeconds: number;
    hourlyMaxSeconds: number;
    sessionMaxSeconds: number;
    isDailyMaxSecondsEnforced: boolean;
    isHourlyMaxSecondsEnforced: boolean;
    isSessionMaxSecondsEnforced: boolean;
    createdAt?: string;
    lastModifiedAt?: string;
    modificationData?: RuleModificationData;
    isStartupDelayEnabled: boolean;
};
