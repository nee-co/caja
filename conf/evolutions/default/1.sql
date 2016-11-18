# create_folders

# --- !Ups
CREATE TABLE `folders` (
  `id` CHAR(36) NOT NULL COMMENT 'UUID',
  `parent_id` VARCHAR(36) NOT NULL COMMENT '上階層フォルダ',
  `group_id` VARCHAR(36) NOT NULL COMMENT 'グループ',
  `name` VARCHAR(50) NOT NULL COMMENT 'フォルダ名',
  `inserted_by` INTEGER NOT NULL COMMENT '作成者',
  `inserted_at` DATETIME NOT NULL COMMENT '作成日時',
  `updated_by` INTEGER NOT NULL COMMENT '更新者',
  `updated_at` DATETIME NOT NULL COMMENT '更新日',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

# --- !Downs
DROP TABLE folders;