-- 课题⑧：Skill 业务分类（企业版按业务场景组织技能，受控词表见前端/文档）
-- 存量行统一归入"其他"，由用户在界面上重新归类
ALTER TABLE skill ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT '其他';
