# create_files

# --- !Ups
CREATE TABLE `files` (
  `id` INTEGER NOT NULL AUTO_INCREMENT COMMENT 'AUTO_INCREMENT',
  `parent_id` INTEGER COMMENT '上階層フォルダ',
  `group_id` INTEGER COMMENT 'グループ',
  `name` VARCHAR(50) NOT NULL COMMENT 'ファイル名',
  `object_key` VARCHAR(255) NOT NULL COMMENT 'オブジェクトキー',
  `inserted_by` INTEGER NOT NULL COMMENT '作成者',
  `inserted_at` DATETIME NOT NULL COMMENT '作成日時',
  `updated_by` INTEGER NOT NULL COMMENT '更新者',
  `updated_at` DATETIME NOT NULL COMMENT '更新日',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

# --- !Downs
DROP TABLE files;