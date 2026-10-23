# Mạng: chỉ CloudFront vào được EC2 (cổng 80), chỉ EC2 vào được RDS. Không SSH; vào máy bằng SSM Session Manager.

data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "EC2 lab 1: chi nhan HTTP tu CloudFront"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "HTTP tu CloudFront"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  egress {
    description = "Ra internet de keo image, goi AWS API"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "RDS lab 1: chi nhan tu EC2"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "PostgreSQL tu EC2"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = data.aws_subnets.default.ids
}

# RDS PostgreSQL một AZ. Lab: không snapshot cuối, không backup tự động (overview.md §23.4 lớp 3).
resource "aws_db_instance" "main" {
  identifier        = local.name
  engine            = "postgres"
  engine_version    = "17"
  instance_class    = "db.t4g.micro"
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "tickethub"
  username = "tickethub"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  skip_final_snapshot     = true
  backup_retention_period = 0
  deletion_protection     = false
  apply_immediately       = true
}
