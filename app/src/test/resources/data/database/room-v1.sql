-- Frozen pre-v2 Room schema used by the v1 -> v7 migration test.
-- Versions 2 through 4 added the subscription_groups columns omitted here.
CREATE TABLE IF NOT EXISTS `subscription_groups` (`id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `userAgent` TEXT NOT NULL, `updateInterval` TEXT NOT NULL, `updateViaProxy` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `lastUpdatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_subscription_groups_position` ON `subscription_groups` (`position`);
CREATE TABLE IF NOT EXISTS `proxy_servers` (`id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `groupId` INTEGER NOT NULL, `serverJson` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_proxy_servers_groupId` ON `proxy_servers` (`groupId`);
CREATE INDEX IF NOT EXISTS `index_proxy_servers_position` ON `proxy_servers` (`position`);
CREATE TABLE IF NOT EXISTS `routing_rules` (`id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `remarks` TEXT NOT NULL, `outboundTag` TEXT NOT NULL, `domainJson` TEXT NOT NULL, `ipJson` TEXT NOT NULL, `processJson` TEXT NOT NULL, `port` TEXT NOT NULL, `protocol` TEXT NOT NULL, `network` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_routing_rules_position` ON `routing_rules` (`position`);
CREATE TABLE IF NOT EXISTS `proxy_app_list_selected_apps` (`packageKey` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`packageKey`));
CREATE INDEX IF NOT EXISTS `index_proxy_app_list_selected_apps_position` ON `proxy_app_list_selected_apps` (`position`);
